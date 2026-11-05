@file:OptIn(kotlin.time.ExperimentalTime::class)
package app.miyoru.data.library


import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.library.model.Favorite
import app.miyoru.domain.library.model.HistoryEntry
import app.miyoru.domain.library.model.MediaProgress
import app.miyoru.domain.source.model.MediaSummary
import app.miyoru.domain.library.repository.LibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private fun app.miyoru.db.MEDIA.toSummary() = MediaSummary(
    MediaKey(SourceId(source_id), MediaType.valueOf(media_type), RemoteId(remote_id)),
    title, cover_url, author,
)

class LibraryRepositoryImpl(
    private val db: MiyoruDatabase,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : LibraryRepository {

    override fun observeFavorites(type: MediaType?): Flow<List<Favorite>> =
        db.libraryQueries.selectFavorites().asFlow().mapToList(io).map { rows ->
            rows.map { Favorite(MediaKey(SourceId(it.source_id), MediaType.valueOf(it.media_type), RemoteId(it.media_id)), it.created_at) }
                .filter { type == null || it.key.mediaType == type }
        }

    override fun observeFavoriteMedia(type: MediaType?): Flow<List<MediaSummary>> =
        db.libraryQueries.selectFavoriteMedia().asFlow().mapToList(io).map { rows ->
            rows.map { it.toSummary() }.filter { type == null || it.key.mediaType == type }
        }

    override fun observeHistory(limit: Int): Flow<List<HistoryEntry>> =
        db.libraryQueries.selectRecentHistory(limit.toLong()).asFlow().mapToList(io).map { rows ->
            rows.map { HistoryEntry(MediaKey(SourceId(it.source_id), MediaType.valueOf(it.media_type), RemoteId(it.media_id)), it.last_opened_at) }
        }

    override suspend fun toggleFavorite(key: MediaKey): Unit = withContext(io) {
        db.transaction {
            val existing = db.libraryQueries.selectFavorite(key.sourceId.value, key.mediaType.name, key.remoteId.value).executeAsOneOrNull()
            if (existing == null) {
                db.libraryQueries.insertFavorite(key.sourceId.value, key.mediaType.name, key.remoteId.value, nowMs(), 0)
            } else {
                db.libraryQueries.deleteFavorite(key.sourceId.value, key.mediaType.name, key.remoteId.value)
            }
        }
    }

    override fun observeProgress(key: MediaKey, unitId: RemoteId): Flow<MediaProgress?> =
        db.libraryQueries.selectProgress(key.sourceId.value, key.mediaType.name, key.remoteId.value, unitId.value)
            .asFlow().mapToList(io).map { rows ->
                rows.singleOrNull()?.let {
                    MediaProgress(key, it.unit_id, it.position, it.total, it.updated_at)
                }
            }

    override suspend fun saveProgress(progress: MediaProgress): Unit = withContext(io) {
        db.libraryQueries.upsertProgress(
            source_id = progress.key.sourceId.value,
            media_type = progress.key.mediaType.name,
            media_id = progress.key.remoteId.value,
            unit_id = progress.unitId,
            mode = "position",
            position = progress.position,
            total = progress.total,
            progress_unit = "unit",
            completed = 0,
            updated_at = progress.updatedAt,
        )
    }

    override fun observeHistoryMedia(limit: Int): Flow<List<MediaSummary>> =
        db.libraryQueries.selectHistoryMedia(limit.toLong()).asFlow().mapToList(io).map { rows ->
            rows.map {
                MediaSummary(
                    MediaKey(SourceId(it.source_id), MediaType.valueOf(it.media_type), RemoteId(it.remote_id)),
                    it.title, it.cover_url, it.author,
                )
            }
        }

    override suspend fun removeHistory(key: MediaKey): Unit = withContext(io) {
        db.libraryQueries.deleteHistory(key.sourceId.value, key.mediaType.name, key.remoteId.value)
    }

    private fun nowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}
