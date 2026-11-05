package app.miyoru.data.download

import app.miyoru.core.common.AppError
import app.miyoru.core.database.InMemoryJvmDriverFactory
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.download.model.DownloadRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DownloadRepositoryImplTest {
    private fun repo() = DownloadRepositoryImpl(MiyoruDatabase(InMemoryJvmDriverFactory().createDriver()))

    @Test
    fun `enqueue pause resume cancel remove`() {
        runBlocking {
            val repo = repo()
            val id = repo.enqueue(DownloadRequest("m1", "u1", "novel_text"))
            assertEquals("QUEUED", repo.observeTasks().first().single().state)
            repo.pause(id)
            assertEquals("PAUSED", repo.observeTasks().first().single().state)
            repo.resume(id)
            assertEquals("QUEUED", repo.observeTasks().first().single().state)
            repo.cancel(id)
            assertEquals("CANCELED", repo.observeTasks().first().single().state)
            repo.remove(id, deleteFiles = false)
            assertTrue(repo.observeTasks().first().isEmpty())
            Unit
        }
    }

    @Test
    fun `resume from queued is invalid`() {
        runBlocking {
            val repo = repo()
            val id = repo.enqueue(DownloadRequest("m1", null, "cover"))
            assertFailsWith<AppError.InvalidInput> { repo.resume(id) }
            Unit
        }
    }

    @Test
    fun `unknown task fails`() {
        runBlocking {
            val repo = repo()
            assertFailsWith<AppError.InvalidInput> { repo.pause("nope") }
            Unit
        }
    }
}
