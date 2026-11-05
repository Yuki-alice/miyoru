package app.miyoru.domain.library.repository

import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.domain.library.model.HistoryEntry
import app.miyoru.domain.library.model.MediaProgress
import app.miyoru.domain.source.model.MediaSummary
import app.miyoru.core.model.RemoteId
import app.miyoru.domain.library.model.Favorite
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeFavorites(type: MediaType? = null): Flow<List<Favorite>>
    /** 收藏的展示用快照（MEDIA JOIN，收藏时写入快照，无快照的不展示）。 */
    fun observeFavoriteMedia(type: MediaType? = null): Flow<List<MediaSummary>>
    fun observeHistory(limit: Int = 100): Flow<List<HistoryEntry>>
    fun observeHistoryMedia(limit: Int = 100): Flow<List<MediaSummary>>
    fun observeProgress(key: MediaKey, unitId: RemoteId): Flow<MediaProgress?>
    suspend fun toggleFavorite(key: MediaKey)
    suspend fun saveProgress(progress: MediaProgress)
    suspend fun removeHistory(key: MediaKey)
}
