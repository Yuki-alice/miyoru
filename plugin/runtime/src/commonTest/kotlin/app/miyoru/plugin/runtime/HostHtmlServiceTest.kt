package app.miyoru.plugin.runtime

import app.miyoru.plugin.runtime.host.HostHtmlService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostHtmlServiceTest {
    private val html = """
        <html><head><title>Demo Title</title></head>
        <body><h1>Hello</h1><p>World <a href="/p1">one</a> <a href="https://allowed.test/p2">two</a></p></body></html>
    """.trimIndent()

    @Test
    fun `title text links extracted`() {
        assertEquals("Demo Title", HostHtmlService.title(html))
        val text = HostHtmlService.text(html)
        assertTrue(text.contains("Hello") && text.contains("World"))
        val links = HostHtmlService.links(html)
        assertEquals(2, links.size)
        assertEquals("/p1", links[0].href)
        assertEquals("two", links[1].text)
        Unit
    }
}
