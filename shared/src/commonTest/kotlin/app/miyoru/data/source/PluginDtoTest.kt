package app.miyoru.data.source

import app.miyoru.core.common.AppError
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.SourceId
import app.miyoru.data.source.model.parseContentUnits
import app.miyoru.data.source.model.parseMediaSummaries
import app.miyoru.data.source.model.toDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PluginDtoTest {
    @Test
    fun `valid summaries map`() {
        val dtos = parseMediaSummaries("""[{"id":"r1","title":"T","cover":"https://x/y.jpg","author":"A"}]""")
        val d = dtos.single().toDomain(SourceId("s"), MediaType.NOVEL)
        assertEquals("T", d.title)
        assertEquals("r1", d.key.remoteId.value)
        Unit
    }

    @Test
    fun `missing id rejected`() {
        val dtos = parseMediaSummaries("""[{"title":"T"}]""")
        assertFailsWith<AppError.Parse> { dtos.single().toDomain(SourceId("s"), MediaType.NOVEL) }
        Unit
    }

    @Test
    fun `non-http cover rejected`() {
        val dtos = parseMediaSummaries("""[{"id":"r1","title":"T","cover":"file:///x"}]""")
        assertFailsWith<AppError.Parse> { dtos.single().toDomain(SourceId("s"), MediaType.NOVEL) }
        Unit
    }

    @Test
    fun `units default title and index`() {
        val units = parseContentUnits("""[{"id":"u1"}]""").map { it.toDomain() }
        assertEquals(listOf("u1"), units.map { it.unitId })
        assertEquals("u1", units.single().title)
        Unit
    }

    @Test
    fun `malformed json throws serialization error`() {
        assertFailsWith<kotlinx.serialization.SerializationException> { parseMediaSummaries("nope") }
        Unit
    }
}
