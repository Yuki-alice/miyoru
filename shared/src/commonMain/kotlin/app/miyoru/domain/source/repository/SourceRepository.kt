package app.miyoru.domain.source.repository

import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.Page
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.domain.source.model.ContentUnit
import app.miyoru.domain.source.model.MediaDetail
import app.miyoru.domain.source.model.MediaSummary
import app.miyoru.domain.source.model.ResolvedContent
import app.miyoru.domain.source.model.SourceInfo
import kotlinx.coroutines.flow.Flow

interface SourceRepository {
    fun observeSources(): Flow<List<SourceInfo>>
    suspend fun search(sourceId: SourceId, type: MediaType, query: String, cursor: String?): Page<MediaSummary>
    suspend fun detail(key: MediaKey): MediaDetail
    suspend fun contentUnits(key: MediaKey): List<ContentUnit>
    suspend fun resolve(unitKey: MediaKey, unitId: RemoteId): ResolvedContent
}
