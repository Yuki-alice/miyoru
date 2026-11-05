package app.miyoru.data.source

import app.miyoru.core.common.AppError
import app.miyoru.core.database.InMemoryJvmDriverFactory
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.data.fixturePackage
import app.miyoru.data.wipeRoot
import app.miyoru.data.zipBytes
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.source.model.ResolvedContent
import app.miyoru.plugin.api.PluginPackageSource
import app.miyoru.plugin.runtime.PluginEngineConfig
import app.miyoru.plugin.runtime.RuntimePluginInstaller
import app.miyoru.plugin.runtime.RuntimeSessionProvider
import app.miyoru.plugin.api.ZipReader
import app.miyoru.plugin.api.zip.CommonZipReader
import app.miyoru.plugin.runtime.install.SystemPluginFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SourceRepositoryContractTest {
    private fun setup(name: String): SourceRepositoryImpl {
        val root = Path("build", "tmp", "contract", name)
        wipeRoot(root)
        val fs = SystemPluginFileSystem(root)
        val zipReader = ZipReader { bytes -> CommonZipReader.read(bytes) }
        val facade = RuntimePluginInstaller(fs, "0.1.0", false, null, zipReader)
        val db = MiyoruDatabase(InMemoryJvmDriverFactory().createDriver())
        val (bundle, manifest) = fixturePackage()
        runBlocking {
            app.miyoru.data.plugin.PluginRepositoryImpl(db, facade)
                .install(PluginPackageSource("test.zip", zipBytes("manifest.json" to manifest, "bundle.js" to bundle)), false)
        }
        val provider = RuntimeSessionProvider(fs, PluginEngineConfig(timeoutMs = 5_000))
        return SourceRepositoryImpl(db, provider, SourceSnapshotStore(db))
    }

    @Test
    fun `search detail chapters resolve flow`() {
        runBlocking {
            val repo = setup("flow")
            val sources = repo.observeSources().first()
            assertEquals(listOf("test.source"), sources.map { it.id.value })
            val page = repo.search(SourceId("test.source"), MediaType.NOVEL, "q", null)
            assertEquals(listOf("T1"), page.items.map { it.title })
            val key = page.items.single().key
            val detail = repo.detail(key)
            assertEquals("T1", detail.summary.title)
            val units = repo.contentUnits(key)
            assertEquals(listOf("u1"), units.map { it.unitId })
            val resolved = repo.resolve(key, RemoteId("u1"))
            assertEquals(ResolvedContent.Novel("hello"), resolved)
            // 快照已落库
            assertTrue(repo.search(SourceId("test.source"), MediaType.NOVEL, "q", null).items.isNotEmpty())
            Unit
        }
    }

    @Test
    fun `missing video capability maps cleanly`() {
        runBlocking {
            val repo = setup("caperr")
            val key = MediaKey(SourceId("test.source"), MediaType.VIDEO, RemoteId("r1"))
            assertFailsWith<AppError.CapabilityUnsupported> { repo.resolve(key, RemoteId("u1")) }
            Unit
        }
    }
}
