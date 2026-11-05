package app.miyoru.data.local.parse

import app.miyoru.core.common.AppError
import app.miyoru.plugin.api.PackageEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParsersTest {
    @Test
    fun `txt chapters split`() {
        val text = "第一章 开始\n\n正文第一段。\n\n第二章 继续\n\n正文第二段。\n"
        val book = TxtParser.parse("book.txt", text.encodeToByteArray())
        assertEquals("book", book.title)
        assertEquals(2, book.chapters.size)
        assertEquals("第一章 开始", book.chapters[0].title)
        Unit
    }

    @Test
    fun `txt without chapters is single unit`() {
        val book = TxtParser.parse("plain.txt", "只有正文没有标题。".encodeToByteArray())
        assertTrue(book.chapters.isEmpty())
        Unit
    }

    @Test
    fun `txt empty rejected`() {
        assertFailsWith<AppError.Parse> { TxtParser.parse("e.txt", ByteArray(0)) }
        Unit
    }

    @Test
    fun `cbz ordering and filtering`() {
        val entries = listOf(
            PackageEntry("b/002.png", byteArrayOf(1)),
            PackageEntry("b/001.jpg", byteArrayOf(2)),
            PackageEntry("__MACOSX/x", byteArrayOf(3)),
            PackageEntry("b/.hidden.jpg", byteArrayOf(4)),
            PackageEntry("b/notes.txt", byteArrayOf(5)),
        )
        val pages = CbzParser.pages(entries)
        assertEquals(listOf("b/001.jpg", "b/002.png"), pages.map { it.name })
        Unit
    }

    @Test
    fun `cbz without images rejected`() {
        assertFailsWith<AppError.Parse> { CbzParser.pages(listOf(PackageEntry("a.txt", byteArrayOf(1)))) }
        Unit
    }

    @Test
    fun `epub parses spine with titles`() {
        val container = """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
        val opf = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Demo Book</dc:title></metadata><manifest><item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="ch2.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>"""
        val xhtml = { body: String -> """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head><body><h1>Hd</h1><p>$body</p></body></html>""" }
        val entries = listOf(
            PackageEntry("mimetype", "application/epub+zip".encodeToByteArray()),
            PackageEntry("META-INF/container.xml", container.encodeToByteArray()),
            PackageEntry("OEBPS/book.opf", opf.encodeToByteArray()),
            PackageEntry("OEBPS/ch1.xhtml", xhtml("first").encodeToByteArray()),
            PackageEntry("OEBPS/ch2.xhtml", xhtml("second").encodeToByteArray()),
        )
        val book = EpubParser.parse(entries)
        assertEquals("Demo Book", book.title)
        assertEquals(2, book.chapters.size)
        assertTrue(book.chapters[0].text.contains("first"))
        Unit
    }

    @Test
    fun `epub missing container rejected`() {
        assertFailsWith<AppError.Parse> { EpubParser.parse(emptyList()) }
        Unit
    }
}
