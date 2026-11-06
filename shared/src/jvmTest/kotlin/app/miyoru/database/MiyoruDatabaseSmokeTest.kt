package app.miyoru.database

import app.miyoru.core.database.InMemoryJvmDriverFactory
import app.miyoru.db.MiyoruDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiyoruDatabaseSmokeTest {

    private fun db() = MiyoruDatabase(InMemoryJvmDriverFactory().createDriver())

    @Test
    fun `plugin insert and select round-trip`() {
        val db = db()
        db.pluginQueries.insertOrReplace(
            id = "example.source",
            name = "Example",
            version = "1.0.0",
            api_version = 1,
            media_types_json = "[\"novel\"]",
            capabilities_json = "[\"search\"]",
            manifest_json = "{}",
            package_sha256 = "abc",
            signature = null,
            repository_id = "demo.repository",
            relative_install_dir = "plugins/example.source",
            previous_version = null,
            previous_package_sha256 = null,
            previous_relative_install_dir = null,
            previous_manifest_json = null,
            enabled = 1,
            last_error = null,
            installed_at = 1L,
            updated_at = 1L,
        )
        val row = db.pluginQueries.selectById("example.source").executeAsOne()
        assertEquals("Example", row.name)
        assertEquals(1, db.pluginQueries.selectAll().executeAsList().size)
    }

    @Test
    fun `media favorite history progress flow`() {
        val db = db()
        db.mediaQueries.insertOrReplace(
            source_id = "s1", media_type = "NOVEL", remote_id = "r1",
            title = "Test Novel", cover_url = null, author = "A",
            description = null, status = null, tags_json = "[]",
            source_name_snapshot = "S1", fetched_at = 1L,
        )
        assertEquals(1, db.mediaQueries.selectByTitle("%Novel%", 10, 0).executeAsList().size)

        db.libraryQueries.insertFavorite("s1", "NOVEL", "r1", 1L, 0)
        assertEquals(1, db.libraryQueries.selectFavorites().executeAsList().size)

        db.libraryQueries.upsertHistory("s1", "NOVEL", "r1", 2L)
        assertEquals(1, db.libraryQueries.selectRecentHistory(10).executeAsList().size)

        db.libraryQueries.upsertProgress("s1", "NOVEL", "r1", "u1", "char", 10, 100, "chars", 0, 3L)
        val p = db.libraryQueries.selectProgress("s1", "NOVEL", "r1", "u1").executeAsOne()
        assertEquals(10L, p.position)
    }

    @Test
    fun `download task and cache entry queries`() {
        val db = db()
        db.downloadQueries.insertTask(
            id = "t1", source_id = "s1", media_type = "NOVEL", media_id = "r1",
            unit_id = "u1", kind = "novel_text", request_json = "{}",
            secret_ref = null, state = "QUEUED", downloaded_bytes = 0,
            total_bytes = null, relative_path = null, error_code = null,
            retry_count = 0, created_at = 1L, updated_at = 1L,
        )
        assertEquals(1, db.downloadQueries.selectTasksByState("QUEUED").executeAsList().size)

        db.cacheQueries.upsertEntry("k1", "cover", "s1", "NOVEL", "r1", 100, 1L, null)
        assertTrue(db.cacheQueries.totalSize().executeAsOne() >= 100)
    }
}
