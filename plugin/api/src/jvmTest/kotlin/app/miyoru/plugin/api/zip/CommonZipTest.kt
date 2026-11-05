package app.miyoru.plugin.api.zip

import app.miyoru.plugin.api.ZipReader
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun makeZip(parts: List<Triple<String, ByteArray, Boolean>>, comment: String = ""): ByteArray {
    val bos = ByteArrayOutputStream()
    ZipOutputStream(bos).apply {
        setComment(comment)
        parts.forEach { (name, bytes, stored) ->
            val e = ZipEntry(name)
            if (stored) {
                e.method = ZipEntry.STORED
                e.size = bytes.size.toLong()
                e.crc = java.util.zip.CRC32().apply { update(bytes) }.value
            }
            putNextEntry(e)
            write(bytes)
            closeEntry()
        }
        finish()
    }
    return bos.toByteArray()
}

class CommonZipTest {
    private val reader: ZipReader = CommonZipReader

    private fun read(bytes: ByteArray) = runBlocking { reader.read(bytes) }

    @Test
    fun `stored and deflated round-trip`() {
        val rnd = Random(42)
        val big = ByteArray(50_000) { rnd.nextInt(256).toByte() }
        val text = "Hello 世界\n".repeat(500).encodeToByteArray()
        val zip = makeZip(
            listOf(
                Triple("a.txt", text, false),
                Triple("dir/b.bin", big, false),
                Triple("plain.txt", "plain".encodeToByteArray(), true),
                Triple("空 dir/名.txt", text, false),
            ),
            comment = "hello-comment",
        )
        val entries = read(zip)
        assertEquals(4, entries.size)
        assertEquals(text.toList(), entries.first { it.path == "a.txt" }.bytes.toList())
        assertEquals(big.toList(), entries.first { it.path == "dir/b.bin" }.bytes.toList())
        assertEquals("plain", entries.first { it.path == "plain.txt" }.bytes.decodeToString())
        assertEquals(text.toList(), entries.first { it.path == "空 dir/名.txt" }.bytes.toList())
        Unit
    }

    @Test
    fun `empty and directory entries`() {
        val zip = makeZip(
            listOf(
                Triple("empty.txt", ByteArray(0), false),
                Triple("sub/", ByteArray(0), true),
            ),
        )
        val entries = read(zip)
        assertTrue(entries.any { it.path == "sub/" && it.bytes.isEmpty() })
        assertEquals(0, entries.first { it.path == "empty.txt" }.bytes.size)
        Unit
    }

    @Test
    fun `crc mismatch rejected`() {
        val zip = makeZip(listOf(Triple("a.txt", "hello".encodeToByteArray(), false)))
        // 破坏压缩数据区附近的一个字节（大概率破坏 crc 或解码）
        zip[zip.size - 30] = (zip[zip.size - 30] + 1).toByte()
        assertFailsWith<Exception> { read(zip) }
        Unit
    }

    @Test
    fun `not a zip rejected`() {
        assertFailsWith<Exception> { read("definitely not a zip".encodeToByteArray()) }
        Unit
    }

    @Test
    fun `epub-shaped package round-trips`() {
        val container = """<?xml version="1.0"?><container version="1.0"><rootfiles><rootfile full-path="OEBPS/book.opf"/></rootfiles></container>"""
        val zip = makeZip(
            listOf(
                Triple("mimetype", "application/epub+zip".encodeToByteArray(), true),
                Triple("META-INF/container.xml", container.encodeToByteArray(), false),
                Triple("OEBPS/book.opf", "<opf/>".encodeToByteArray(), false),
            ),
        )
        val entries = read(zip)
        assertEquals(3, entries.size)
        assertTrue(entries.any { it.path == "META-INF/container.xml" })
        Unit
    }
}
