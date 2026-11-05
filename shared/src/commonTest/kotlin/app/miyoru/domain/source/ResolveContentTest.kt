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
import app.miyoru.domain.source.usecase.ResolveContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private class FakeRepo(val handler: (MediaKey, RemoteId) -> ResolvedContent) : SourceRepository {
    override fun observeSources(): Flow<List<SourceInfo>> = flowOf(emptyList())
    override suspend fun search(sourceId: SourceId, type: MediaType, query: String, cursor: String?) = Page<MediaSummary>(emptyList())
    override suspend fun detail(key: MediaKey): MediaDetail = error("not needed")
    override suspend fun contentUnits(key: MediaKey): List<ContentUnit> = emptyList()
    override suspend fun resolve(unitKey: MediaKey, unitId: RemoteId): ResolvedContent = handler(unitKey, unitId)
}

class ResolveContentTest {
    private val key = MediaKey(SourceId("s"), MediaType.NOVEL, RemoteId("r"))

    @Test
    fun `passthrough on success`() = runBlocking {
        val useCase = ResolveContent(FakeRepo { _, _ -> ResolvedContent.Novel("hello") })
        assertEquals(ResolvedContent.Novel("hello"), useCase(key, RemoteId("u1")))
        Unit
    }

    @Test
    fun `io maps to network error`() = runBlocking {
        val useCase = ResolveContent(FakeRepo { _, _ -> throw IOException("down") })
        val err = assertFailsWith<AppError.Network> { useCase(key, RemoteId("u1")) }
        assertIs<IOException>(err.cause)
        Unit
    }

    @Test
    fun `cancellation is not swallowed`() = runBlocking {
        val useCase = ResolveContent(FakeRepo { _, _ -> throw CancellationException("gone") })
        assertFailsWith<CancellationException> { useCase(key, RemoteId("u1")) }
        Unit
    }
}
