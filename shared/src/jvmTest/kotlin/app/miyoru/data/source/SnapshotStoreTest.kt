package app.miyoru.data.source

import app.miyoru.core.database.InMemoryJvmDriverFactory
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.source.model.MediaSummary
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotStoreTest {
    private fun store() = SourceSnapshotStore(MiyoruDatabase(InMemoryJvmDriverFactory().createDriver()))

    @Test
    fun `save and load by source`() {
        runBlocking {
            val store = store()
            val items = listOf(
                MediaSummary(MediaKey(SourceId("s"), MediaType.COMIC, RemoteId("r1")), "T1", null, null),
            )
            store.saveSummaries(SourceId("s"), MediaType.COMIC, "Src", items, 1)
            assertEquals(listOf("T1"), store.loadBySource(SourceId("s"), MediaType.COMIC).map { it.title })
            Unit
        }
    }
}
