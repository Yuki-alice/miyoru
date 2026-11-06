package app.miyoru.dev

import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.Page
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.domain.download.model.DownloadRequest
import app.miyoru.domain.download.model.DownloadTask
import app.miyoru.domain.download.repository.DownloadRepository
import app.miyoru.domain.library.model.Favorite
import app.miyoru.domain.library.model.HistoryEntry
import app.miyoru.domain.library.model.MediaProgress
import app.miyoru.domain.library.repository.LibraryRepository
import app.miyoru.domain.source.model.ContentUnit
import app.miyoru.domain.source.model.MediaDetail
import app.miyoru.domain.source.model.MediaSummary
import app.miyoru.domain.source.model.ResolvedContent
import app.miyoru.domain.source.model.SourceInfo
import app.miyoru.domain.source.repository.SourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * 设计对照用的演示数据（仅 `-Dmiyoru.demo=true` 时装配）。
 *
 * 目的：让 17 个屏在没有安装任何源插件时也能呈现接近设计稿的内容密度，
 * 便于逐屏比对版式。数据与文案取自 `build_prototype.py` 的原型定义，
 * 不联网、不写入用户数据库，正式构建不会加载本模块。
 */
object DemoContent {
    private fun key(source: String, type: MediaType, id: String) = MediaKey(SourceId(source), type, RemoteId(id))

    private fun cover(seed: String) = "https://picsum.photos/seed/$seed/480/640"

    private fun summary(title: String, type: MediaType, source: String, id: String, author: String?, seed: String) =
        MediaSummary(key(source, type, id), title, cover(seed), author)

    val changYe = summary("长夜余火", MediaType.COMIC, "mando", "changye", "灰鹰", "miyoru-1")
    val shanHai = summary("山海拾遗", MediaType.NOVEL, "qingwen", "shanyai", "南舟", "miyoru-2")
    val liMing = summary("黎明之前", MediaType.VIDEO, "yingku", "liming", "林一", "miyoru-3")
    val wuZhong = summary("雾中列车", MediaType.COMIC, "mando", "wuzhong", "灰鹰", "miyoru-4")
    val beiJie = summary("北街七号", MediaType.COMIC, "mando", "beijie", "阿岚", "miyoru-5")
    val baiZhou = summary("白昼尽头", MediaType.COMIC, "mando", "baizhou", "阿岚", "miyoru-6")
    val huiSheng = summary("回声纪年", MediaType.VIDEO, "yingku", "huisheng", "周野", "miyoru-7")

    val favorites = listOf(changYe, shanHai, liMing, wuZhong, baiZhou)
    val history = listOf(changYe, liMing, shanHai, wuZhong, huiSheng)
    val catalog = listOf(changYe, wuZhong, beiJie, baiZhou, shanHai, liMing)

    fun modules(): List<Module> = listOf(demoLibraryModule(), demoSourceModule(), demoDownloadModule())
}

private fun demoLibraryModule(): Module = module {
    single<LibraryRepository> { DemoLibraryRepository() }
}

private fun demoSourceModule(): Module = module {
    single<SourceRepository> { DemoSourceRepository() }
}

private fun demoDownloadModule(): Module = module {
    single<DownloadRepository> { DemoDownloadRepository() }
}

private class DemoLibraryRepository : LibraryRepository {
    private val favorites = MutableStateFlow(DemoContent.favorites.map { Favorite(it.key, 0L) })
    private val history = MutableStateFlow(DemoContent.history.map { HistoryEntry(it.key, 0L) })

    private fun summaryOf(key: MediaKey): MediaSummary? =
        (DemoContent.catalog + DemoContent.history + DemoContent.favorites).firstOrNull { it.key == key }

    override fun observeFavorites(type: MediaType?): Flow<List<Favorite>> =
        favorites.map { list -> if (type == null) list else list.filter { it.key.mediaType == type } }

    override fun observeFavoriteMedia(type: MediaType?): Flow<List<MediaSummary>> =
        favorites.map { list ->
            list.filter { type == null || it.key.mediaType == type }.mapNotNull { summaryOf(it.key) }
        }

    override fun observeHistory(limit: Int): Flow<List<HistoryEntry>> = history.map { it.take(limit) }

    override fun observeHistoryMedia(limit: Int): Flow<List<MediaSummary>> =
        history.map { list -> list.take(limit).mapNotNull { summaryOf(it.key) } }

    override fun observeProgress(key: MediaKey, unitId: RemoteId): Flow<MediaProgress?> =
        flowOf(MediaProgress(key, unitId.value, 12, 24, 0L))

    override suspend fun toggleFavorite(key: MediaKey) {
        val current = favorites.value
        favorites.value = if (current.any { it.key == key }) current.filterNot { it.key == key }
        else current + Favorite(key, 0L)
    }

    override suspend fun saveProgress(progress: MediaProgress) = Unit

    override suspend fun removeHistory(key: MediaKey) {
        history.value = history.value.filterNot { it.key == key }
    }
}

private class DemoSourceRepository : SourceRepository {
    private val sources = listOf(
        SourceInfo(SourceId("mando"), "漫岛", listOf(MediaType.COMIC), listOf("search", "detail", "pages"), true),
        SourceInfo(SourceId("qingwen"), "轻文阁", listOf(MediaType.NOVEL), listOf("search", "detail", "chapters", "content"), true),
        SourceInfo(SourceId("yingku"), "影库", listOf(MediaType.VIDEO), listOf("search", "detail", "streams"), false),
    )

    override fun observeSources(): Flow<List<SourceInfo>> = flowOf(sources)

    override suspend fun search(sourceId: SourceId, type: MediaType, query: String, cursor: String?): Page<MediaSummary> =
        Page(DemoContent.catalog.filter { it.key.sourceId == sourceId }, null)

    override suspend fun detail(key: MediaKey): MediaDetail {
        val summary = DemoContent.catalog.firstOrNull { it.key == key } ?: DemoContent.changYe
        return MediaDetail(
            summary = summary,
            sourceName = sources.firstOrNull { it.id == summary.key.sourceId }?.name ?: "本地",
            attributes = mapOf(
                "status" to "连载中 · 每周五更新",
                "tags" to "奇幻,悬疑",
                "intro" to "永夜之城里，没有人记得上一次日出是什么时候。少年在钟楼下接过一盏旧灯，" +
                    "开始沿着屋檐向北走——那里据说还留着最后一段白昼。",
            ),
        )
    }

    override suspend fun contentUnits(key: MediaKey): List<ContentUnit> {
        val noun = if (key.mediaType == MediaType.VIDEO) "EP" else "第"
        return (1..24).map { index ->
            val title = when (key.mediaType) {
                MediaType.VIDEO -> "EP${index.toString().padStart(2, '0')} · ${listOf("回声", "空白来信", "天亮之前")[index % 3]}"
                else -> "$noun ${index} 话 · ${listOf("回声", "空楼", "钟声")[index % 3]}"
            }
            ContentUnit("u$index", title, index.toLong())
        }
    }

    override suspend fun resolve(unitKey: MediaKey, unitId: RemoteId): ResolvedContent =
        when (unitKey.mediaType) {
            MediaType.NOVEL -> ResolvedContent.Novel(
                "夜色像潮水一样漫过街道。\n\n他把灯熄灭，等第三声钟响。\n\n窗外，有人正沿着屋檐往北走。",
            )
            MediaType.COMIC -> ResolvedContent.Comic(
                (1..8).map { app.miyoru.domain.source.model.ImageResource("https://picsum.photos/seed/page$it/800/1200") },
            )
            MediaType.VIDEO -> ResolvedContent.Video(emptyList())
        }
}

private class DemoDownloadRepository : DownloadRepository {
    private val tasks = MutableStateFlow(
        listOf(
            DownloadTask("《长夜余火》 第 12 话", "DONE"),
            DownloadTask("《黎明之前》 EP06", "DONE"),
            DownloadTask("《山海拾遗》 第 3 卷", "RUNNING"),
            DownloadTask("《雾中列车》 第 08 话", "QUEUED"),
        ),
    )

    override fun observeTasks(): Flow<List<DownloadTask>> = tasks

    override suspend fun enqueue(request: DownloadRequest): String = "task-${tasks.value.size + 1}"

    override suspend fun pause(taskId: String) {
        tasks.value = tasks.value.map { if (it.id == taskId) it.copy(state = "PAUSED") else it }
    }

    override suspend fun resume(taskId: String) {
        tasks.value = tasks.value.map { if (it.id == taskId) it.copy(state = "RUNNING") else it }
    }

    override suspend fun cancel(taskId: String) {
        tasks.value = tasks.value.filterNot { it.id == taskId }
    }

    override suspend fun remove(taskId: String, deleteFiles: Boolean) {
        tasks.value = tasks.value.filterNot { it.id == taskId }
    }
}
