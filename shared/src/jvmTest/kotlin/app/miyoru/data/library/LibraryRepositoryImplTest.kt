package app.miyoru.data.library

import app.miyoru.core.database.InMemoryJvmDriverFactory
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.library.model.MediaProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryRepositoryImplTest {
    private val key = MediaKey(SourceId("s"), MediaType.NOVEL, RemoteId("r"))
    private fun repo() = LibraryRepositoryImpl(MiyoruDatabase(InMemoryJvmDriverFactory().createDriver()))

    @Test
    fun `toggle adds then removes`() {
        runBlocking {
            val repo = repo()
            repo.toggleFavorite(key)
            assertEquals(1, repo.observeFavorites().first().size)
            repo.toggleFavorite(key)
            assertTrue(repo.observeFavorites().first().isEmpty())
            Unit
        }
    }

    @Test
    fun `progress save and observe round-trip`() {
        runBlocking {
            val repo = repo()
            assertNull(repo.observeProgress(key, RemoteId("u1")).first())
            repo.saveProgress(MediaProgress(key, "u1", 5, 50, 7))
            assertEquals(5L, repo.observeProgress(key, RemoteId("u1")).first()?.position)
            Unit
        }
    }

    @Test
    fun `remove history on empty is no-op`() {
        runBlocking {
            val repo = repo()
            repo.removeHistory(key)
            assertTrue(repo.observeHistory().first().isEmpty())
            Unit
        }
    }
}
