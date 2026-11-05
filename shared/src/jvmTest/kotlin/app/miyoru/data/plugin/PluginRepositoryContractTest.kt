package app.miyoru.data.plugin

import app.miyoru.core.common.AppError
import app.miyoru.core.database.InMemoryJvmDriverFactory
import app.miyoru.data.fixturePackage
import app.miyoru.data.wipeRoot
import app.miyoru.data.zipBytes
import app.miyoru.db.MiyoruDatabase
import app.miyoru.plugin.api.PluginPackageSource
import app.miyoru.plugin.runtime.RuntimePluginInstaller
import app.miyoru.plugin.api.ZipReader
import app.miyoru.plugin.api.zip.CommonZipReader
import app.miyoru.plugin.runtime.install.SystemPluginFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PluginRepositoryContractTest {
    private fun setup(name: String): Triple<PluginRepositoryImpl, SystemPluginFileSystem, MiyoruDatabase> {
        val root = Path("build", "tmp", "contract", name)
        wipeRoot(root)
        val fs = SystemPluginFileSystem(root)
        val zipReader = ZipReader { bytes -> CommonZipReader.read(bytes) }
        val facade = RuntimePluginInstaller(fs, "0.1.0", false, null, zipReader)
        val db = MiyoruDatabase(InMemoryJvmDriverFactory().createDriver())
        return Triple(PluginRepositoryImpl(db, facade), fs, db)
    }

    private fun pkgBytes(version: String = "1.0.0"): ByteArray {
        val (bundle, manifest) = fixturePackage(version)
        return zipBytes("manifest.json" to manifest, "bundle.js" to bundle)
    }

    @Test
    fun `install observe disable uninstall`() {
        runBlocking {
            val (repo) = setup("lifecycle")
            val installed = repo.install(PluginPackageSource("test-1.0.0.zip", pkgBytes()), enforceSignature = false)
            assertEquals("test.source", installed.id)
            assertEquals(1, repo.observeInstalled().first().size)
            repo.setEnabled("test.source", false)
            assertEquals(false, repo.observeInstalled().first().single().enabled)
            repo.uninstall("test.source")
            assertTrue(repo.observeInstalled().first().isEmpty())
            Unit
        }
    }

    @Test
    fun `update via install then rollback restores previous`() {
        runBlocking {
            val (repo) = setup("rollback")
            repo.install(PluginPackageSource("v1.zip", pkgBytes("1.0.0")), false)
            repo.install(PluginPackageSource("v2.zip", pkgBytes("2.0.0")), false)
            assertEquals("2.0.0", repo.observeInstalled().first().single().version)
            val rolled = repo.rollback("test.source")
            assertEquals("1.0.0", rolled.version)
            assertEquals("1.0.0", repo.observeInstalled().first().single().version)
            Unit
        }
    }

    @Test
    fun `index-driven update is explicit capability error`() {
        runBlocking {
            val (repo) = setup("update-stub")
            assertFailsWith<AppError.CapabilityUnsupported> { repo.update("test.source") }
            Unit
        }
    }
}
