package app.miyoru.domain.source.usecase

import app.miyoru.core.common.toAppError
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.SourceId
import app.miyoru.domain.source.model.MediaSummary
import app.miyoru.domain.source.model.SourceInfo
import app.miyoru.domain.source.repository.SourceRepository
import app.miyoru.core.common.AppError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 跨源聚合搜索：单源独立超时、部分成功、错误隔离。
 * - 新搜索由调用方取消旧协程；页面离开取消传播（CancellationException 一律重抛）。
 * - 单个源超时/抛错只污染该源条目，不导致全局失败。
 */
data class SourceSearchResult(
    val sourceId: SourceId,
    val items: List<MediaSummary> = emptyList(),
    val nextCursor: String? = null,
    val error: AppError? = null,
    val elapsedMs: Long = 0,
)

data class AggregatedSearchResult(
    val query: String,
    val results: List<SourceSearchResult>,
) {
    val allItems: List<MediaSummary> get() = results.flatMap { it.items }
    val hasPartialFailure: Boolean get() = results.any { it.error != null }
}

class SearchAcrossSources(
    private val repository: SourceRepository,
    private val defaultPerSourceTimeoutMs: Long = 15_000,
) {
    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(
        type: MediaType,
        query: String,
        sourceIds: List<SourceId>? = null,
        perSourceTimeoutMs: Long = defaultPerSourceTimeoutMs,
    ): AggregatedSearchResult {
        val ids = sourceIds ?: repository.observeSources().first().map(SourceInfo::id)
        val results = supervisorScope {
            ids.map { id ->
                async {
                    val start = Clock.System.now().toEpochMilliseconds()
                    try {
                        val page = withTimeout(perSourceTimeoutMs) {
                            repository.search(id, type, query, null)
                        }
                        SourceSearchResult(
                            sourceId = id,
                            items = page.items,
                            nextCursor = page.nextCursor,
                            elapsedMs = Clock.System.now().toEpochMilliseconds() - start,
                        )
                    } catch (e: Exception) {
                        SourceSearchResult(
                            sourceId = id,
                            error = e.toAppError("search"),
                            elapsedMs = Clock.System.now().toEpochMilliseconds() - start,
                        )
                    }
                }
            }.awaitAll()
        }
        return AggregatedSearchResult(query = query, results = results)
    }
}
