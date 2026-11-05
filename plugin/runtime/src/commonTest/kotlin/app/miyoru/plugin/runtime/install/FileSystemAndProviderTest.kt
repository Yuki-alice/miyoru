package app.miyoru.plugin.runtime.install

import app.miyoru.plugin.api.PluginCall
import app.miyoru.plugin.runtime.PluginEngineConfig
import app.miyoru.plugin.runtime.RuntimeSessionProvider
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import app.miyoru.plugin.api.PackageEntry

class FileSystemAndProviderTest {
    private fun fs(name: String): SystemPluginFileSystem {
        val root = Path("build", "tmp", "pluginfs", name)
        wipe(root)
        return SystemPluginFileSystem(root)
    }

    private fun wipe(p: Path) {
        val meta = SystemFileSystem.metadataOrNull(p) ?: return
        if (meta.isDirectory) SystemFileSystem.list(p).forEach { wipe(it) }
        SystemFileSystem.delete(p, mustExist = false)
    }

    @Test
    fun `system fs write read move delete`() {
        runBlocking {
            val fs = fs("basic")
            fs.writeFile("a/b.txt", "hi".encodeToByteArray())
            assertEquals("hi", fs.readFile("a/b.txt").decodeToString())
            assertTrue(fs.exists("a/b.txt"))
            fs.move("a", "c")
            assertTrue(fs.exists("c/b.txt"))
            assertTrue(!fs.exists("a/b.txt"))
            fs.delete("c")
            assertTrue(!fs.exists("c/b.txt"))
            Unit
        }
    }

    @Test
    fun `install then provide session and invoke`() {
        runBlocking {
            val fs = fs("provider")
            val bundle = "globalThis.search = function(p){ return {q: p.q}; };".encodeToByteArray()
            val manifest = """
                {"id":"example.source","name":"Example","version":"1.0.0","apiVersion":1,
                 "minAppVersion":"0.1.0","mediaTypes":["novel"],"capabilities":["search"],
                 "permissions":{"domains":["https://example.com"]},
                 "entry":"bundle.js","entrySha256":"${Sha256.hex(bundle)}"}
            """.trimIndent().encodeToByteArray()
            val installer = PluginInstaller(fs, PluginPackageValidator("0.1.0", enforceSignature = false))
            installer.install(listOf(PackageEntry("manifest.json", manifest), PackageEntry("bundle.js", bundle)))

            val provider = RuntimeSessionProvider(fs, PluginEngineConfig(timeoutMs = 5_000))
            val session = provider.session("example.source")
            val res = session.invoke(PluginCall("search", mapOf("q" to "hi")))
            assertTrue(res.ok)
            assertTrue(res.json.contains("hi"))
            provider.release("example.source")
            Unit
        }
    }
}
