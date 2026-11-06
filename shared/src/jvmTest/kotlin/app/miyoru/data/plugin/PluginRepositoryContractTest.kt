package app.miyoru.data.plugin

import app.miyoru.core.common.AppError
import app.miyoru.core.database.InMemoryJvmDriverFactory
import app.miyoru.data.fixturePackage
import app.miyoru.data.wipeRoot
import app.miyoru.data.zipBytes
import app.miyoru.db.MiyoruDatabase
import app.miyoru.plugin.api.PluginPackageSource
import app.miyoru.plugin.api.ZipReader
import app.miyoru.plugin.api.zip.CommonZipReader
import app.miyoru.plugin.runtime.RuntimePluginInstaller
import app.miyoru.plugin.runtime.install.SystemPluginFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val REPO = "demo.repository"

class PluginRepositoryContractTest {
    private fun setup(name: String): Pair<PluginRepositoryImpl, MiyoruDatabase> {
        val root = Path("build", "tmp", "contract", name)
        wipeRoot(root)
        val fs = SystemPluginFileSystem(root)
        val zipReader = ZipReader { bytes -> CommonZipReader.read(bytes) }
        val facade = RuntimePluginInstaller(fs, "0.1.0", false, null, zipReader)
        val db = MiyoruDatabase(InMemoryJvmDriverFactory().createDriver())
        return PluginRepositoryImpl(db, facade) to db
    }

    private fun pkgBytes(version: String = "1.0.0"): ByteArray {
        val (bundle, manifest) = fixturePackage(version)
        return zipBytes("manifest.json" to manifest, "bundle.js" to bundle)
    }

    private fun cacheUpdate(db: MiyoruDatabase, repositoryId: String, version: String, pluginId: String = "test.source") {
        db.pluginRepositoryQueries.insertOrReplaceCachedPlugin(
            repository_id = repositoryId,
            plugin_id = pluginId,
            name = "Test Source",
            version = version,
            api_version = 1,
            min_app_version = "0.1.0",
            media_types_json = "[\"novel\"]",
            capabilities_json = "[\"search\"]",
            domains_json = "[\"https://example.com\"]",
            author = null,
            homepage = null,
            icon_url = null,
            description = null,
            download_url = "https://cdn.example.com/test.zip",
            package_sha256 = "a".repeat(64),
        )
    }

    @Test
    fun `install from repository then disable and uninstall`() {
        runBlocking {
            val (repo, _) = setup("lifecycle")
            val installed = repo.installFromRepository(
                PluginPackageSource("test-1.0.0.zip", pkgBytes()),
                REPO, "test.source", false,
            )
            assertEquals("test.source", installed.id)
            assertEquals(REPO, installed.repositoryId)
            assertEquals(REPO, repo.observeInstalled().first().single().repositoryId)
            repo.setEnabled("test.source", false)
            assertEquals(false, repo.observeInstalled().first().single().enabled)
            repo.uninstall("test.source")
            assertTrue(repo.observeInstalled().first().isEmpty())
            Unit
        }
    }

    @Test
    fun `second install keeps source repository and rollback restores previous`() {
        runBlocking {
            val (repo, _) = setup("rollback")
            repo.installFromRepository(PluginPackageSource("v1.zip", pkgBytes("1.0.0")), REPO, "test.source", false)
            repo.installFromRepository(PluginPackageSource("v2.zip", pkgBytes("2.0.0")), REPO, "test.source", false)
            assertEquals("2.0.0", repo.observeInstalled().first().single().version)
            val rolled = repo.rollback("test.source")
            assertEquals("1.0.0", rolled.version)
            assertEquals(REPO, rolled.repositoryId)
            Unit
        }
    }

    @Test
    fun `checkForUpdate only reads the source repository cache`() {
        runBlocking {
            val (repo, db) = setup("updates")
            repo.installFromRepository(PluginPackageSource("v1.zip", pkgBytes("1.0.0")), REPO, "test.source", false)
            assertNull(repo.checkForUpdate("test.source"))
            cacheUpdate(db, REPO, "2.0.0")
            val update = repo.checkForUpdate("test.source")
            assertEquals("2.0.0", update?.latestVersion)
            assertEquals(REPO, update?.repositoryId)
            // 另一个仓库里的同名插件不构成更新来源。
            repo.uninstall("test.source")
            repo.installFromRepository(PluginPackageSource("v1.zip", pkgBytes("1.0.0")), REPO, "test.source", false)
            cacheUpdate(db, "other.repository", "9.0.0")
            assertEquals("2.0.0", repo.checkForUpdate("test.source")?.latestVersion)
            Unit
        }
    }

    @Test
    fun `cross repository reinstall is rejected and old install survives`() {
        runBlocking {
            val (repo, _) = setup("cross-repo")
            repo.installFromRepository(PluginPackageSource("v1.zip", pkgBytes("1.0.0")), REPO, "test.source", false)
            assertFailsWith<AppError.PluginSourceMismatch> {
                repo.installFromRepository(
                    PluginPackageSource("v2.zip", pkgBytes("2.0.0")),
                    "other.repository", "test.source", false,
                )
            }
            val row = repo.observeInstalled().first().single()
            assertEquals("1.0.0", row.version)
            assertEquals(REPO, row.repositoryId)
            Unit
        }
    }

    @Test
    fun `manifest id mismatch is rejected`() {
        runBlocking {
            val (repo, _) = setup("id-mismatch")
            assertFailsWith<AppError.InvalidInput> {
                repo.installFromRepository(
                    PluginPackageSource("v1.zip", pkgBytes()), REPO, "another.source", false,
                )
            }
            assertTrue(repo.observeInstalled().first().isEmpty())
            Unit
        }
    }
}
