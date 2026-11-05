@file:OptIn(kotlin.time.ExperimentalTime::class)
package app.miyoru.data.local


import app.miyoru.core.common.AppError
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.data.local.parse.CbzParser
import app.miyoru.data.local.parse.EpubParser
import app.miyoru.data.local.parse.TxtParser
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.local.model.LocalFormat
import app.miyoru.domain.local.model.LocalImportDescriptor
import app.miyoru.domain.local.model.LocalMedia
import app.miyoru.domain.local.repository.LocalImportRepository
import app.miyoru.plugin.api.ZipReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write
import kotlin.time.Clock

/**
 * 本地导入实现（P0）：inbox 读源文件 → 解析 → 拷入书库 → MEDIA + CONTENT_UNIT 落库。
 * 正文按需从书库文件重读，不进 DB（无迁移负担）。
 * source_id 固定 "local"，remote_id 即书库内相对路径。
 */
class LocalImportRepositoryImpl(
    private val db: MiyoruDatabase,
    private val zipReader: ZipReader,
    private val inboxRoot: String,
    private val libraryRoot: String,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : LocalImportRepository {

    override fun observeLocalMedia(): Flow<List<LocalMedia>> {
        val novels = flow { emit(db.mediaQueries.selectBySource("local", "NOVEL", 1000, 0).executeAsList()) }.flowOn(io)
        val comics = flow { emit(db.mediaQueries.selectBySource("local", "COMIC", 1000, 0).executeAsList()) }.flowOn(io)
        return combine(novels, comics) { novelsRows, comicsRows ->
            (novelsRows + comicsRows).map {
                LocalMedia(
                    MediaKey(SourceId(it.source_id), MediaType.valueOf(it.media_type), RemoteId(it.remote_id)),
                    it.title,
                    if (it.media_type == "COMIC") LocalFormat.CBZ else formatOf(it.remote_id),
                    it.fetched_at,
                )
            }
        }
    }

    override suspend fun import(descriptor: LocalImportDescriptor): LocalMedia = withContext(io) {
        val srcBytes = readInbox(descriptor.relativePath)
        val targetRel = descriptor.relativePath
        val title: String
        val units: List<Pair<String, String>>
        when (descriptor.format) {
            LocalFormat.TXT -> {
                val book = TxtParser.parse(descriptor.fileName, srcBytes)
                title = book.title
                units = book.chapters.mapIndexed { i, c -> "c$i" to c.title }
                if (units.isEmpty()) {
                    // 无章节：整本一个单元
                    units.toMutableList().add("c0" to book.title)
                }
            }
            LocalFormat.EPUB -> {
                val book = EpubParser.parse(zipReader.read(srcBytes))
                title = book.title
                units = book.chapters.mapIndexed { i, c -> "c$i" to c.title }
            }
            LocalFormat.CBZ -> {
                val pages = CbzParser.pages(zipReader.read(srcBytes))
                title = descriptor.fileName.substringBeforeLast(".")
                units = pages.map { "p${it.index}" to it.name.substringAfterLast("/") }
            }
        }
        writeLibrary(targetRel, srcBytes)
        val now = Clock.System.now().toEpochMilliseconds()
        val mediaType = if (descriptor.format == LocalFormat.CBZ) MediaType.COMIC else MediaType.NOVEL
        db.transaction {
            db.mediaQueries.insertOrReplace(
                "local", mediaType.name, targetRel, title, null, null, null, null,
                "[]", "本地导入", now,
            )
            db.contentUnitQueries.deleteByMedia("local", mediaType.name, targetRel)
            units.forEachIndexed { i, (uid, utitle) ->
                db.contentUnitQueries.insertOrReplace(
                    "local", mediaType.name, targetRel, uid, utitle, i.toLong(), null, now,
                )
            }
        }
        LocalMedia(MediaKey(SourceId("local"), mediaType, RemoteId(targetRel)), title, descriptor.format, now)
    }

    override suspend fun remove(key: MediaKey) = withContext(io) {
        // 先删文件，再清 MEDIA/单元/收藏/历史/进度索引。
        deleteLibrary(key.remoteId.value)
        db.transaction {
            db.mediaQueries.deleteMedia(key.sourceId.value, key.mediaType.name, key.remoteId.value)
            db.contentUnitQueries.deleteByMedia(key.sourceId.value, key.mediaType.name, key.remoteId.value)
            db.libraryQueries.deleteFavorite(key.sourceId.value, key.mediaType.name, key.remoteId.value)
            db.libraryQueries.deleteHistory(key.sourceId.value, key.mediaType.name, key.remoteId.value)
            db.libraryQueries.deleteProgress(key.sourceId.value, key.mediaType.name, key.remoteId.value)
        }
    }

    private fun formatOf(relativePath: String): LocalFormat = when (relativePath.substringAfterLast(".", "").lowercase()) {
        "epub" -> LocalFormat.EPUB
        else -> LocalFormat.TXT
    }

    private fun readInbox(relativePath: String): ByteArray {
        val p = Path(inboxRoot, relativePath)
        if (SystemFileSystem.metadataOrNull(p) == null) throw AppError.InvalidInput("inbox file missing: $relativePath")
        return SystemFileSystem.source(p).buffered().use { it.readByteArray() }
    }

    private fun writeLibrary(relativePath: String, bytes: ByteArray) {
        val target = Path(libraryRoot, relativePath)
        val parent = target.parent
        if (parent != null) SystemFileSystem.createDirectories(parent)
        SystemFileSystem.sink(target).buffered().use { it.write(bytes) }
    }

    private fun deleteLibrary(relativePath: String) {
        fun wipe(p: Path) {
            val meta = SystemFileSystem.metadataOrNull(p) ?: return
            if (meta.isDirectory) SystemFileSystem.list(p).forEach { wipe(it) }
            SystemFileSystem.delete(p, mustExist = false)
        }
        wipe(Path(libraryRoot, relativePath))
    }
}
