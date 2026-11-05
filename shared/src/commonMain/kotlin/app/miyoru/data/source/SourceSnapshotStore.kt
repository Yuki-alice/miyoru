package app.miyoru.data.source

import app.miyoru.core.model.MediaType
import app.miyoru.core.model.SourceId
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.source.model.MediaSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 远端结果的本地快照：收藏/历史在源禁用或卸载后仍可展示（快照原则）。
 * 缓存数据可清理、可覆盖，不做事实来源。
 */
class SourceSnapshotStore(
    private val db: MiyoruDatabase,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun saveSummaries(
        sourceId: SourceId,
        type: MediaType,
        sourceName: String,
        items: List<MediaSummary>,
        fetchedAt: Long,
    ) = withContext(io) {
        db.transaction {
            items.forEach {
                db.mediaQueries.insertOrReplace(
                    source_id = it.key.sourceId.value,
                    media_type = it.key.mediaType.name,
                    remote_id = it.key.remoteId.value,
                    title = it.title,
                    cover_url = it.coverUrl,
                    author = it.author,
                    description = null,
                    status = null,
                    tags_json = "[]",
                    source_name_snapshot = sourceName,
                    fetched_at = fetchedAt,
                )
            }
        }
    }

    suspend fun loadBySource(sourceId: SourceId, type: MediaType, limit: Long = 100): List<MediaSummary> =
        withContext(io) {
            db.mediaQueries.selectBySource(sourceId.value, type.name, limit, 0).executeAsList().map {
                MediaSummary(
                    key = app.miyoru.core.model.MediaKey(
                        SourceId(it.source_id), MediaType.valueOf(it.media_type), app.miyoru.core.model.RemoteId(it.remote_id),
                    ),
                    title = it.title,
                    coverUrl = it.cover_url,
                    author = it.author,
                )
            }
        }
}
