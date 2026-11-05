@file:OptIn(kotlin.time.ExperimentalTime::class)
package app.miyoru.data.source


import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.miyoru.core.common.AppError
import app.miyoru.core.common.toAppError
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.Page
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.data.source.model.DetailEnvelope
import app.miyoru.data.source.model.NovelEnvelope
import app.miyoru.data.source.model.PagesEnvelope
import app.miyoru.data.source.model.SearchEnvelope
import app.miyoru.data.source.model.StreamsEnvelope
import app.miyoru.data.source.model.UnitsEnvelope
import app.miyoru.data.source.model.toDomain
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.source.model.ContentUnit
import app.miyoru.domain.source.model.MediaDetail
import app.miyoru.domain.source.model.MediaSummary
import app.miyoru.domain.source.model.ImageResource
import app.miyoru.domain.source.model.ResolvedContent
import app.miyoru.domain.source.model.StreamResource
import app.miyoru.domain.source.model.SourceInfo
import app.miyoru.domain.source.repository.SourceRepository
import app.miyoru.plugin.api.PluginSessionProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private val jsonLenient = Json { ignoreUnknownKeys = true; isLenient = true }

class SourceRepositoryImpl(
    private val db: MiyoruDatabase,
    private val sessions: PluginSessionProvider,
    private val snapshots: SourceSnapshotStore,
    private val io: CoroutineDispatcher = Dispatchers.Default,
    private val perCallTimeoutMs: Long = 15_000,
) : SourceRepository {

    override fun observeSources(): Flow<List<SourceInfo>> =
        db.pluginQueries.selectAll().asFlow().mapToList(io).map { rows ->
            rows.map {
                SourceInfo(
                    id = SourceId(it.id),
                    name = it.name,
                    mediaTypes = decodeStrings(it.media_types_json).mapNotNull { s ->
                        runCatching { MediaType.valueOf(s) }.getOrNull()
                    },
                    capabilities = decodeStrings(it.capabilities_json),
                    enabled = it.enabled == 1L,
                )
            }
        }

    override suspend fun search(sourceId: SourceId, type: MediaType, query: String, cursor: String?): Page<MediaSummary> =
        withContext(io) {
            try {
                val raw = withTimeout(perCallTimeoutMs) {
                    sessions.invokeCall(
                        sourceId.value, "search",
                        mapOf("type" to type.name, "query" to query, "cursor" to (cursor ?: "")),
                        "search",
                    )
                }
                val env = jsonLenient.decodeFromString<SearchEnvelope>(raw)
                val items = env.items.map { it.toDomain(sourceId, type) }
                val name = runCatching {
                    db.pluginQueries.selectById(sourceId.value).executeAsOne().name
                }.getOrElse { "" }
                snapshots.saveSummaries(sourceId, type, name, items, Clock.System.now().toEpochMilliseconds())
                Page(items, env.nextCursor)
            } catch (e: Exception) {
                throw e.toAppError("search")
            }
        }

    override suspend fun detail(key: MediaKey): MediaDetail = withContext(io) {
        try {
            val raw = withTimeout(perCallTimeoutMs) {
                sessions.invokeCall(
                    key.sourceId.value, "detail",
                    mapOf("type" to key.mediaType.name, "id" to key.remoteId.value),
                    "detail",
                )
            }
            val env = jsonLenient.decodeFromString<DetailEnvelope>(raw)
            val summary = MediaSummary(
                key = key,
                title = env.title?.takeIf { it.isNotBlank() }
                    ?: throw AppError.Parse("detail missing title"),
                coverUrl = env.cover,
                author = env.author,
            )
            val sourceName = runCatching {
                db.pluginQueries.selectById(key.sourceId.value).executeAsOne().name
            }.getOrElse { "" }
            val attributes = env.attributes.toMutableMap()
            env.description?.let { attributes["description"] = it }
            env.status?.let { attributes["status"] = it }
            if (env.tags.isNotEmpty()) attributes["tags"] = env.tags.joinToString(",")
            MediaDetail(summary, sourceName, attributes)
        } catch (e: Exception) {
            throw e.toAppError("detail")
        }
    }

    override suspend fun contentUnits(key: MediaKey): List<ContentUnit> = withContext(io) {
        try {
            val raw = withTimeout(perCallTimeoutMs) {
                sessions.invokeCall(
                    key.sourceId.value, "chapters",
                    mapOf("type" to key.mediaType.name, "id" to key.remoteId.value),
                    "chapters",
                )
            }
            val env = jsonLenient.decodeFromString<UnitsEnvelope>(raw)
            env.units.map { it.toDomain() }
        } catch (e: Exception) {
            throw e.toAppError("chapters")
        }
    }

    override suspend fun resolve(unitKey: MediaKey, unitId: RemoteId): ResolvedContent = withContext(io) {
        val capability = when (unitKey.mediaType) {
            MediaType.NOVEL -> "content"
            MediaType.COMIC -> "pages"
            MediaType.VIDEO -> "streams"
        }
        try {
            val raw = withTimeout(perCallTimeoutMs) {
                sessions.invokeCall(
                    unitKey.sourceId.value, capability,
                    mapOf("type" to unitKey.mediaType.name, "id" to unitKey.remoteId.value, "unitId" to unitId.value),
                    "resolve",
                )
            }
            when (unitKey.mediaType) {
                MediaType.NOVEL -> ResolvedContent.Novel(
                    jsonLenient.decodeFromString<NovelEnvelope>(raw).text
                        ?: throw AppError.Parse("novel missing text"),
                )
                MediaType.COMIC -> ResolvedContent.Comic(
                    jsonLenient.decodeFromString<PagesEnvelope>(raw).pages.map { ImageResource(it) },
                )
                MediaType.VIDEO -> ResolvedContent.Video(
                    jsonLenient.decodeFromString<StreamsEnvelope>(raw).streams.map { StreamResource(it) },
                )
            }
        } catch (e: Exception) {
            throw e.toAppError("resolve")
        }
    }

    private fun decodeStrings(raw: String): List<String> =
        runCatching { jsonLenient.decodeFromString<List<String>>(raw) }.getOrElse { emptyList() }
}
