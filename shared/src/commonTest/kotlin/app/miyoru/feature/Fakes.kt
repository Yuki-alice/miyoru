package app.miyoru.feature

import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.domain.library.model.Favorite
import app.miyoru.domain.library.model.HistoryEntry
import app.miyoru.domain.library.model.MediaProgress
import app.miyoru.domain.library.repository.LibraryRepository
import app.miyoru.domain.source.model.ContentUnit
import app.miyoru.domain.source.model.MediaDetail
import app.miyoru.domain.source.model.MediaSummary
import app.miyoru.domain.source.model.ResolvedContent
import app.miyoru.domain.source.model.SourceInfo
import app.miyoru.domain.source.repository.SourceRepository
import app.miyoru.core.model.Page
import app.miyoru.core.model.SourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

fun media(source: String, type: MediaType, remote: String, title: String): MediaSummary =
    MediaSummary(MediaKey(SourceId(source), type, RemoteId(remote)), title, null, null)

class FakeLibraryRepository : LibraryRepository {
    val favorites = MutableStateFlow<List<Favorite>>(emptyList())
    val history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val media = mutableListOf<MediaSummary>()
    val progress = mutableMapOf<String, MediaProgress>()

    override fun observeFavorites(type: MediaType?) =
        favorites.map { list -> list.filter { type == null || it.key.mediaType == type } }
    override fun observeFavoriteMedia(type: MediaType?) =
        favorites.map { favs ->
            favs.mapNotNull { f -> media.firstOrNull { it.key == f.key } }
                .filter { type == null || it.key.mediaType == type }
        }
    override fun observeHistory(limit: Int) = history.map { it.take(limit) }
    override fun observeHistoryMedia(limit: Int) =
        history.map { hs -> hs.mapNotNull { h -> media.firstOrNull { it.key == h.key } }.take(limit) }
    override fun observeProgress(key: MediaKey, unitId: RemoteId): Flow<MediaProgress?> =
        flowOf(progress[key.toString() + unitId.value])
    override suspend fun toggleFavorite(key: MediaKey) {
        val cur = favorites.value
        favorites.value = if (cur.any { it.key == key }) cur.filterNot { it.key == key }
        else cur + Favorite(key, 1)
    }
    override suspend fun saveProgress(p: MediaProgress) { progress[p.key.toString() + p.unitId] = p }
    override suspend fun removeHistory(key: MediaKey) {
        history.value = history.value.filterNot { it.key == key }
    }
}

class FakeSourceRepository(
    var detail: MediaDetail? = null,
    var units: List<ContentUnit> = emptyList(),
    var fail: Boolean = false,
    var sources: List<SourceInfo> = emptyList(),
    var searchHandler: (SourceId, String) -> Page<MediaSummary> = { _, _ -> Page(emptyList()) },
    var resolveHandler: (MediaKey, RemoteId) -> ResolvedContent = { _, _ -> ResolvedContent.Novel("text") },
) : SourceRepository {
    override fun observeSources(): Flow<List<SourceInfo>> = flowOf(sources)
    override suspend fun search(sourceId: SourceId, type: MediaType, query: String, cursor: String?) =
        searchHandler(sourceId, query)
    override suspend fun detail(key: MediaKey): MediaDetail =
        if (fail) throw RuntimeException("down") else detail ?: throw RuntimeException("no detail")
    override suspend fun contentUnits(key: MediaKey): List<ContentUnit> {
        if (fail) throw RuntimeException("down")
        return units
    }
    override suspend fun resolve(unitKey: MediaKey, unitId: RemoteId): ResolvedContent =
        resolveHandler(unitKey, unitId)
}
