package app.miyoru.data.plugin

import app.miyoru.core.common.AppError
import app.miyoru.core.database.InMemoryJvmDriverFactory
import app.miyoru.data.fixturePackage
import app.miyoru.data.wipeRoot
import app.miyoru.data.zipBytes
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.plugin.model.CatalogEntryState
import app.miyoru.plugin.api.Sha256
import app.miyoru.plugin.api.ZipReader
import app.miyoru.plugin.api.zip.CommonZipReader
import app.miyoru.plugin.runtime.RuntimePluginInstaller
import app.miyoru.plugin.runtime.install.SystemPluginFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val INDEX_URL = "https://raw.example.com/repo/index.json"
private const val REPO_ID = "demo.repository"

private fun indexJson(
    version: String,
    sha: String,
    name: String = "Demo Repository",
    signingKey: String? = null,
) = """
{
  "schemaVersion": 1,
  "repositoryId": "$REPO_ID",
  "name": "$name",
  "signingKey": ${if (signingKey == null) "null" else "\"$signingKey\""},
  "description": "测试仓库",
  "generatedAt": 1790000000000,
  "plugins": [{
    "id": "test.source",
    "name": "Test Source",
    "version": "$version",
    "apiVersion": 1,
    "minAppVersion": "0.1.0",
    "mediaTypes": ["novel"],
    "capabilities": ["search", "detail", "chapters"],
    "domains": ["https://example.com"],
    "downloadUrl": "https://cdn.example.com/plugins/test-$version.zip",
    "packageSha256": "$sha"
  }]
}
""".trimIndent()

private fun pkg(version: String): ByteArray {
    val (bundle, manifest) = fixturePackage(version)
    return zipBytes("manifest.json" to manifest, "bundle.js" to bundle)
}

/** 完全离线的仓库闭环测试：MockEngine 提供索引与包，真实 DB/FS/安装器。 */
private class Harness(name: String) {
    private var indexBytes: ByteArray
    private var packageBytes: ByteArray
    var failIndex = false

    private val http = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val url = request.url.toString()
                when {
                    url.contains("index.json") && failIndex -> respondError(HttpStatusCode.BadGateway)
                    url.contains("index.json") -> respond(indexBytes, HttpStatusCode.OK)
                    url.contains(".zip") -> respond(packageBytes, HttpStatusCode.OK)
                    else -> respondError(HttpStatusCode.NotFound)
                }
            }
        }
    }

    private val root = Path("build", "tmp", "catalog", name)
    private val fs = SystemPluginFileSystem(root)
    val db: MiyoruDatabase
    val plugins: PluginRepositoryImpl
    val catalog: PluginCatalogRepositoryImpl

    init {
        wipeRoot(root)
        packageBytes = pkg("1.0.0")
        indexBytes = indexJson("1.0.0", Sha256.hex(packageBytes)).encodeToByteArray()
        val zipReader = ZipReader { bytes -> CommonZipReader.read(bytes) }
        val facade = RuntimePluginInstaller(fs, "0.1.0", false, null, zipReader)
        db = MiyoruDatabase(InMemoryJvmDriverFactory().createDriver())
        plugins = PluginRepositoryImpl(db, facade)
        catalog = PluginCatalogRepositoryImpl(
            db = db,
            client = RepositoryIndexClient(http),
            previewStore = RepositoryPreviewStore(),
            pluginRepository = plugins,
            appVersion = "0.1.0",
        )
    }

    fun setRemote(version: String, zip: ByteArray = pkg(version), name: String = "Demo Repository") {
        packageBytes = zip
        indexBytes = indexJson(version, Sha256.hex(zip), name).encodeToByteArray()
    }

    /** 索引声明的哈希与服务器实际返回的包不一致。 */
    fun corruptIndexHash() {
        indexBytes = indexJson("1.0.0", "a".repeat(64)).encodeToByteArray()
    }

    /** 仓库更换发布者公钥：身份指纹变化，必须重新确认信任。 */
    fun changeSigningKey() {
        indexBytes = indexJson("1.0.0", Sha256.hex(packageBytes), signingKey = "B".repeat(44))
            .encodeToByteArray()
    }

    fun insertOtherRepositoryWithSamePlugin() {
        db.pluginRepositoryQueries.insertRepository(
            id = "other.repository",
            url = "https://raw.example.com/other/index.json",
            name = "Other", description = null, website_url = null, icon_url = null,
            schema_version = 1, signing_key = null, fingerprint = "b".repeat(64),
            enabled = 1, last_updated = 1L, last_error = null, plugin_count = 1, created_at = 1L,
        )
        db.pluginRepositoryQueries.insertOrReplaceCachedPlugin(
            repository_id = "other.repository", plugin_id = "test.source",
            name = "Test Source", version = "1.0.0", api_version = 1,
            min_app_version = "0.1.0", media_types_json = "[\"novel\"]",
            capabilities_json = "[\"search\"]", domains_json = "[\"https://example.com\"]",
            author = null, homepage = null, icon_url = null, description = null,
            download_url = "https://cdn.example.com/plugins/other.zip",
            package_sha256 = "a".repeat(64),
        )
    }
}

private suspend fun addRepository(h: Harness) {
    val preview = h.catalog.inspectRepository(INDEX_URL)
    h.catalog.addRepository(preview.fingerprint)
}

private fun saveFavorite(db: MiyoruDatabase) {
    db.mediaQueries.insertOrReplace(
        source_id = "test.source", media_type = "NOVEL", remote_id = "r1",
        title = "T", cover_url = null, author = null, description = null,
        status = null, tags_json = "[]", source_name_snapshot = "Test", fetched_at = 1L,
    )
    db.libraryQueries.insertFavorite("test.source", "NOVEL", "r1", 1L, 0)
}

class PluginCatalogRepositoryImplTest {

    @Test
    fun `inspect does not persist until user confirms trust`() {
        runBlocking {
            val h = Harness("inspect")
            val preview = h.catalog.inspectRepository(INDEX_URL)
            assertEquals(REPO_ID, preview.repositoryId)
            assertEquals(1, preview.pluginCount)
            assertTrue(h.catalog.observeRepositories().first().isEmpty())
            h.catalog.addRepository(preview.fingerprint)
            assertEquals(1, h.catalog.observeRepositories().first().size)
            val snapshot = h.catalog.observeCatalog().first()
            assertEquals(1, snapshot.groups.size)
            assertEquals(CatalogEntryState.AVAILABLE, snapshot.groups.single().entries.single().state)
            Unit
        }
    }

    @Test
    fun `on demand install writes repository binding and enables updates`() {
        runBlocking {
            val h = Harness("install")
            addRepository(h)
            val installed = h.catalog.install(REPO_ID, "test.source")
            assertEquals(REPO_ID, installed.repositoryId)
            assertEquals("1.0.0", h.plugins.observeInstalled().first().single().version)
            assertEquals(
                CatalogEntryState.INSTALLED,
                h.catalog.observeCatalog().first().groups.single().entries.single().state,
            )

            h.setRemote("2.0.0")
            val refreshed = h.catalog.refreshRepository(REPO_ID)
            assertEquals(1, refreshed.refreshed)
            assertEquals("2.0.0", h.catalog.checkForUpdates().single().latestVersion)
            assertEquals("2.0.0", h.catalog.update("test.source").version)
            assertEquals(REPO_ID, h.plugins.observeInstalled().first().single().repositoryId)
            Unit
        }
    }

    @Test
    fun `package hash mismatch is rejected before install`() {
        runBlocking {
            val h = Harness("hash")
            addRepository(h)
            h.corruptIndexHash()
            // 缓存里仍是正确哈希，但服务器返回了一个哈希不同的包。
            h.setRemote("1.0.0", pkg("1.0.1"))
            assertFailsWith<AppError.InvalidInput> { h.catalog.install(REPO_ID, "test.source") }
            assertTrue(h.plugins.observeInstalled().first().isEmpty())
            Unit
        }
    }

    @Test
    fun `signing key change requires reconfirmation and keeps old index cache`() {
        runBlocking {
            val h = Harness("fingerprint")
            addRepository(h)
            h.changeSigningKey()
            val outcome = h.catalog.refreshRepository(REPO_ID)
            assertEquals(listOf(REPO_ID), outcome.fingerprintChanged)
            assertEquals("Demo Repository", h.catalog.observeRepositories().first().single().name)
            assertTrue(h.catalog.observeRepositories().first().single().lastError!!.contains("指纹"))
            assertEquals(1, h.catalog.observeCatalog().first().groups.single().entries.size)

            val again = h.catalog.inspectRepository(INDEX_URL)
            h.catalog.addRepository(again.fingerprint)
            assertEquals(0, h.catalog.refreshRepository(REPO_ID).fingerprintChanged.size)
            Unit
        }
    }

    @Test
    fun `refresh failure keeps last verified index`() {
        runBlocking {
            val h = Harness("offline")
            addRepository(h)
            h.failIndex = true
            val outcome = h.catalog.refreshAll()
            assertEquals(1, outcome.failed.size)
            assertEquals(1, h.catalog.observeCatalog().first().groups.single().entries.size)
            Unit
        }
    }

    @Test
    fun `remove repository without plugins keeps them as orphans`() {
        runBlocking {
            val h = Harness("orphan")
            addRepository(h)
            h.catalog.install(REPO_ID, "test.source")
            saveFavorite(h.db)
            h.catalog.removeRepository(REPO_ID, removePlugins = false)
            assertEquals(1, h.plugins.observeInstalled().first().size)
            assertEquals(1, h.catalog.observeCatalog().first().orphans.size)
            assertNull(h.plugins.checkForUpdate("test.source"))
            assertTrue(h.db.libraryQueries.selectFavorites().executeAsList().isNotEmpty())
            Unit
        }
    }

    @Test
    fun `remove repository with plugins uninstalls them but keeps library data`() {
        runBlocking {
            val h = Harness("remove")
            addRepository(h)
            h.catalog.install(REPO_ID, "test.source")
            saveFavorite(h.db)
            h.catalog.removeRepository(REPO_ID, removePlugins = true)
            assertTrue(h.plugins.observeInstalled().first().isEmpty())
            assertTrue(h.db.libraryQueries.selectFavorites().executeAsList().isNotEmpty())
            Unit
        }
    }

    @Test
    fun `cross repository install is rejected`() {
        runBlocking {
            val h = Harness("cross")
            addRepository(h)
            h.catalog.install(REPO_ID, "test.source")
            h.insertOtherRepositoryWithSamePlugin()
            assertFailsWith<AppError.PluginSourceMismatch> {
                h.catalog.install("other.repository", "test.source")
            }
            Unit
        }
    }
}
