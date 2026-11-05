package app.miyoru.plugin.runtime.install

import app.miyoru.plugin.api.PluginManifest
import app.miyoru.plugin.api.PluginPermissions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import app.miyoru.plugin.api.PackageEntry

private val json = Json { ignoreUnknownKeys = true }

private fun manifestBytes(entrySha: String, entry: String = "bundle.js"): ByteArray {
    val m = PluginManifest(
        id = "example.source", name = "Example", version = "1.0.0", apiVersion = 1,
        minAppVersion = "0.1.0", mediaTypes = listOf("novel"), capabilities = listOf("search"),
        permissions = PluginPermissions(listOf("https://example.com")),
        entry = entry, entrySha256 = entrySha,
    )
    return json.encodeToString(m).encodeToByteArray()
}

private fun pkg(entrySha: String, extra: List<PackageEntry> = emptyList()): List<PackageEntry> {
    val bundle = "globalThis.search = 1;".encodeToByteArray()
    return listOf(
        PackageEntry("manifest.json", manifestBytes(Sha256.hex(bundle))),
        PackageEntry("bundle.js", bundle),
    ) + extra
}

class ValidatorInstallerTest {
    private fun validator() = PluginPackageValidator("0.1.0", enforceSignature = false)

    @Test
    fun `valid package passes`() {
        runBlocking {
            val v = validator().validate(pkg(""))
            assertEquals("example.source", v.manifest.id)
            Unit
        }
    }

    @Test
    fun `zip slip rejected`() {
        runBlocking {
            assertFailsWith<PackageException> {
                validator().validate(pkg("") + PackageEntry("../evil.js", byteArrayOf(1)))
            }
            Unit
        }
    }

    @Test
    fun `hash mismatch rejected`() {
        runBlocking {
            val bundle = "tampered".encodeToByteArray()
            val entries = listOf(
                PackageEntry("manifest.json", manifestBytes(Sha256.hex("other".encodeToByteArray()))),
                PackageEntry("bundle.js", bundle),
            )
            assertFailsWith<PackageException> { validator().validate(entries) }
            Unit
        }
    }

    @Test
    fun `install then rollback`() {
        runBlocking {
            val fs = FakeFs()
            val installer = PluginInstaller(fs, validator())
            val r1 = installer.install(pkg(""))
            assertEquals("1.0.0", r1.manifest.version)
            assertTrue(fs.exists("plugins/example.source/bundle.js"))
            // 第二版覆盖安装
            val bundle2 = "globalThis.search = 2;".encodeToByteArray()
            val m2 = json.decodeFromString<PluginManifest>(manifestBytes("").decodeToString())
                .copy(version = "2.0.0")
            val entries2 = listOf(
                PackageEntry("manifest.json", json.encodeToString(m2).encodeToByteArray()),
                PackageEntry("bundle.js", bundle2),
            )
            // 注意：m2 的 entrySha256 仍是旧值，需要按新 bundle 重算
            val fixed = entries2.map {
                if (it.path == "manifest.json") {
                    PackageEntry(it.path, json.encodeToString(m2.copy(entrySha256 = Sha256.hex(bundle2))).encodeToByteArray())
                } else it
            }
            val r2 = installer.install(fixed)
            assertTrue(r2.hadPrevious)
            assertEquals(bundle2.toList(), fs.readFile("plugins/example.source/bundle.js").toList())
            installer.rollback("example.source")
            assertEquals("globalThis.search = 1;".encodeToByteArray().toList(), fs.readFile("plugins/example.source/bundle.js").toList())
            Unit
        }
    }

    @Test
    fun `failed validation keeps old version`() {
        runBlocking {
            val fs = FakeFs()
            val installer = PluginInstaller(fs, validator())
            installer.install(pkg(""))
            assertFailsWith<PackageException> {
                installer.install(listOf(PackageEntry("manifest.json", "{}".encodeToByteArray())))
            }
            assertTrue(fs.exists("plugins/example.source/bundle.js"))
            Unit
        }
    }
}
