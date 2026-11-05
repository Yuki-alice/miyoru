package app.miyoru.domain.library

import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.domain.library.model.Favorite
import app.miyoru.domain.library.model.HistoryEntry
import app.miyoru.domain.library.model.MediaProgress
import app.miyoru.domain.source.model.MediaSummary
import app.miyoru.domain.library.repository.LibraryRepository
import app.miyoru.domain.library.usecase.UpdateProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeLibrary(val saved: MutableList<MediaProgress> = mutableListOf()) : LibraryRepository {
    override fun observeFavorites(type: MediaType?) = flowOf<List<Favorite>>(emptyList())
    override fun observeFavoriteMedia(type: MediaType?) = flowOf<List<MediaSummary>>(emptyList())
    override fun observeHistory(limit: Int) = flowOf<List<HistoryEntry>>(emptyList())
    override fun observeHistoryMedia(limit: Int) = flowOf<List<MediaSummary>>(emptyList())
    override fun observeProgress(key: MediaKey, unitId: RemoteId) = flowOf<MediaProgress?>(null)
    override suspend fun toggleFavorite(key: MediaKey) = Unit
    override suspend fun saveProgress(progress: MediaProgress) { saved.add(progress) }
    override suspend fun removeHistory(key: MediaKey) = Unit
}

class UpdateProgressTest {
    private val key = MediaKey(SourceId("s"), MediaType.VIDEO, RemoteId("r"))

    @Test
    fun `normal progress passes through`() = runBlocking {
        val repo = FakeLibrary()
        val out = UpdateProgress(repo)(MediaProgress(key, "u1", 30, 100, 1))
        assertEquals(30L, out.position)
        assertEquals(1, repo.saved.size)
        Unit
    }

    @Test
    fun `position clamped to total`() = runBlocking {
        val repo = FakeLibrary()
        val out = UpdateProgress(repo)(MediaProgress(key, "u1", 999, 100, 1))
        assertEquals(100L, out.position)
        assertEquals(100L, repo.saved.single().position)
        Unit
    }

    @Test
    fun `negative position clamped to zero`() = runBlocking {
        val repo = FakeLibrary()
        val out = UpdateProgress(repo)(MediaProgress(key, "u1", -5, 100, 1))
        assertEquals(0L, out.position)
        Unit
    }
}
