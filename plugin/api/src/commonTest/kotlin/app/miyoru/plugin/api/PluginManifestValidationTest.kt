package app.miyoru.plugin.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun good() = PluginManifest(
    id = "example.source",
    name = "Example",
    version = "1.0.0",
    apiVersion = 1,
    minAppVersion = "0.1.0",
    author = "Example",
    homepage = "https://example.com",
    mediaTypes = listOf("novel"),
    capabilities = listOf("search", "detail"),
    permissions = PluginPermissions(listOf("https://example.com")),
    entry = "bundle.js",
    entrySha256 = "a".repeat(64),
    signature = null,
)

class PluginManifestValidationTest {
    @Test
    fun `valid manifest passes`() {
        assertTrue(good().validate("0.1.0", enforceSignature = false).isEmpty())
    }

    @Test
    fun `bad id version api rejected`() {
        val p = good().copy(id = "Bad ID!", version = "x", apiVersion = 99).validate("0.1.0", false)
        assertEquals(3, p.size)
    }

    @Test
    fun `too new minApp rejected`() {
        assertTrue(good().copy(minAppVersion = "9.9.9").validate("0.1.0", false).isNotEmpty())
    }

    @Test
    fun `private hosts rejected`() {
        listOf("http://example.com", "https://127.0.0.1", "https://localhost", "https://192.168.1.1", "https://10.0.0.1")
            .forEach { assertTrue(!isValidHttpsOrigin(it), it) }
        assertTrue(isValidHttpsOrigin("https://example.com/api"))
    }

    @Test
    fun `signature enforced when required`() {
        assertTrue(good().validate("0.1.0", enforceSignature = true).any { it.contains("signature") })
    }

    @Test
    fun `path traversal entry rejected`() {
        assertTrue(good().copy(entry = "../evil.js").validate("0.1.0", false).isNotEmpty())
        assertTrue(good().copy(entry = "/abs.js").validate("0.1.0", false).isNotEmpty())
    }
}
