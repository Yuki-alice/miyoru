package app.miyoru.data.local

import app.miyoru.core.database.InMemoryJvmDriverFactory
import app.miyoru.core.model.MediaType
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.local.model.LocalFormat
import app.miyoru.domain.local.model.LocalImportDescriptor
import app.miyoru.plugin.api.ZipReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalImportTest {
    private fun wipe(path: Path) {
        val meta = SystemFileSystem.metadataOrNull(path) ?: return
        if (meta.isDirectory) SystemFileSystem.list(path).forEach { wipe(it) }
        SystemFileSystem.delete(path, mustExist = false)
    }

    private fun repo(name: String): Triple<LocalImportRepositoryImpl, String, MiyoruDatabase> {
        val base = "build/tmp/local/$name"
        wipe(Path(base))
        val db = MiyoruDatabase(InMemoryJvmDriverFactory().createDriver())
        val zip = ZipReader { bytes ->
            // 测试桩：按约定首字节选择内容
            if (bytes.decodeToString().startsWith("EPUB")) {
                val container = """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
                val opf = """<?xml version="1.0"?><package><metadata><dc:title xmlns:dc="urn:x">EB</dc:title></metadata><manifest><item id="c1" href="c1.xhtml" media-type="x"/></manifest><spine><itemref idref="c1"/></spine></package>"""
                val xhtml = """<html><head><title>t</title></head><body><p>body1</p></body></html>"""
                listOf(
                    app.miyoru.plugin.api.PackageEntry("META-INF/container.xml", container.encodeToByteArray()),
                    app.miyoru.plugin.api.PackageEntry("book.opf", opf.encodeToByteArray()),
                    app.miyoru.plugin.api.PackageEntry("c1.xhtml", xhtml.encodeToByteArray()),
                )
            } else {
                val names = bytes.decodeToString().split(";")
                names.map { app.miyoru.plugin.api.PackageEntry(it, byteArrayOf(1)) }
            }
        }
        return Triple(LocalImportRepositoryImpl(db, zip, "$base/inbox", "$base/library"), base, db)
    }

    private fun dropInbox(base: String, rel: String, bytes: ByteArray) {
        val p = Path(base, "inbox", rel)
        val parent = p.parent
        if (parent != null) SystemFileSystem.createDirectories(parent)
        SystemFileSystem.sink(p).buffered().use { it.write(bytes) }
    }

    @Test
    fun `txt import creates media and units`() {
        runBlocking {
            val (repo, base) = repo("txt")
            dropInbox(base, "novel.txt", "第一章 开头\n\n正文。\n\n第二章 发展\n\n更多正文。\n".encodeToByteArray())
            val media = repo.import(LocalImportDescriptor("novel.txt", LocalFormat.TXT, 10, "novel.txt"))
            assertEquals("novel", media.title)
            assertEquals(1, repo.observeLocalMedia().first().size)
            // 书库文件已拷贝
            assertTrue(SystemFileSystem.exists(Path(base, "library", "novel.txt")))
            Unit
        }
    }

    @Test
    fun `cbz import indexes pages`() {
        runBlocking {
            val (repo, base, db) = repo("cbz")
            dropInbox(base, "c.cbz", "CBZ".encodeToByteArray())
            // 桩按分号切文件名
            val zip = ZipReader { _ ->
                listOf(
                    app.miyoru.plugin.api.PackageEntry("002.png", byteArrayOf(1)),
                    app.miyoru.plugin.api.PackageEntry("001.jpg", byteArrayOf(2)),
                )
            }
            val repo2 = LocalImportRepositoryImpl(db, zip, "$base/inbox", "$base/library")
            val media = repo2.import(LocalImportDescriptor("c.cbz", LocalFormat.CBZ, 3, "c.cbz"))
            assertEquals(MediaType.COMIC, media.key.mediaType)
            val units = db.contentUnitQueries.selectByMedia("local", "COMIC", "c.cbz").executeAsList()
            assertEquals(listOf("001.jpg", "002.png"), units.map { it.title })
            // 删除清理文件与索引
            repo2.remove(media.key)
            assertTrue(repo2.observeLocalMedia().first().isEmpty())
            assertTrue(!SystemFileSystem.exists(Path(base, "library", "c.cbz")))
            Unit
        }
    }
}
