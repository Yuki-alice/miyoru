package app.miyoru.domain.source

import app.miyoru.core.common.AppError
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
import app.miyoru.domain.source.repository.SourceRepository
import app.miyoru.domain.source.usecase.SearchAcrossSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun key(source: String, title: String) = MediaKey(SourceId(source), MediaType.NOVEL, RemoteId(title))

private class FakeSourceRepository(
    private val sources: List<SourceInfo>,
    private val handlers: Map<String, suspend () -> Page<MediaSummary>>,
) : SourceRepository {
    override fun observeSources(): Flow<List<SourceInfo>> = flowOf(sources)
    override suspend fun search(sourceId: SourceId, type: MediaType, query: String, cursor: String?): Page<MediaSummary> =
        handlers[sourceId.value]?.invoke() ?: Page(emptyList())
    override suspend fun detail(key: MediaKey): MediaDetail = error("not needed")
    override suspend fun contentUnits(key: MediaKey): List<ContentUnit> = error("not needed")
    override suspend fun resolve(unitKey: MediaKey, unitId: RemoteId): ResolvedContent = error("not needed")
}

private fun info(id: String) = SourceInfo(SourceId(id), id)

class SearchAcrossSourcesTest {

    @Test
    fun `all sources success merges items`() = runBlocking {
        val repo = FakeSourceRepository(
            listOf(info("a"), info("b")),
            mapOf(
                "a" to { Page(listOf(MediaSummary(key("a", "t1"), "t1"))) },
                "b" to { Page(listOf(MediaSummary(key("b", "t2"), "t2")), "cursor-b") },
            ),
        )
        val result = SearchAcrossSources(repo)(MediaType.NOVEL, "q")
        assertEquals(2, result.allItems.size)
        assertEquals(false, result.hasPartialFailure)
        assertEquals("cursor-b", result.results.first { it.sourceId.value == "b" }.nextCursor)
        Unit
    }

    @Test
    fun `single source failure does not fail others`() = runBlocking {
        val repo = FakeSourceRepository(
            listOf(info("good"), info("bad")),
            mapOf(
                "good" to { Page(listOf(MediaSummary(key("good", "t"), "t"))) },
                "bad" to { throw IOException("boom") },
            ),
        )
        val result = SearchAcrossSources(repo)(MediaType.NOVEL, "q")
        assertEquals(1, result.allItems.size)
        assertTrue(result.hasPartialFailure)
        val bad = result.results.first { it.sourceId.value == "bad" }
        assertIs<AppError.Network>(bad.error)
        assertTrue(bad.items.isEmpty())
        Unit
    }

    @Test
    fun `slow source hits per-source timeout`() = runBlocking {
        val repo = FakeSourceRepository(
            listOf(info("fast"), info("slow")),
            mapOf(
                "fast" to { Page(listOf(MediaSummary(key("fast", "t"), "t"))) },
                "slow" to { delay(500); Page(emptyList()) },
            ),
        )
        val result = SearchAcrossSources(repo, defaultPerSourceTimeoutMs = 50)(MediaType.NOVEL, "q")
        assertEquals(1, result.allItems.size)
        val slow = result.results.first { it.sourceId.value == "slow" }
        assertIs<AppError.Timeout>(slow.error)
        assertTrue(slow.elapsedMs < 1000)
        Unit
    }

    @Test
    fun `outer cancellation propagates instead of partial result`() = runBlocking {
        val repo = FakeSourceRepository(
            listOf(info("slow")),
            mapOf("slow" to { delay(10_000); Page(emptyList()) }),
        )
        val job = launch {
            assertFailsWith<CancellationException> {
                SearchAcrossSources(repo, defaultPerSourceTimeoutMs = 60_000)(MediaType.NOVEL, "q")
            }
        }
        delay(100)
        job.cancelAndJoin()
        Unit
    }
}
