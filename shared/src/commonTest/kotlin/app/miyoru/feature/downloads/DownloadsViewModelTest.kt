package app.miyoru.feature.downloads

import app.miyoru.domain.download.model.DownloadTask
import app.miyoru.feature.FakeDownloadEngine
import app.miyoru.feature.FakeDownloadRepository
import app.miyoru.feature.downloads.presentation.DownloadsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    @Test
    fun `lists tasks and pauses`() = runTest {
        val repo = FakeDownloadRepository()
        repo.tasks.value = listOf(DownloadTask("t1", "QUEUED"))
        val engine = FakeDownloadEngine()
        val vm = DownloadsViewModel(repo, engine)
        assertEquals(1, vm.uiState.first { !it.isLoading }.tasks.size)
        vm.pause("t1")
        testScheduler.advanceUntilIdle()
        assertEquals("PAUSED", vm.uiState.first { !it.isLoading }.tasks.single().state)
        Unit
    }

    @Test
    fun `start resumes then runs engine`() = runTest {
        val repo = FakeDownloadRepository()
        repo.tasks.value = listOf(DownloadTask("t1", "PAUSED"))
        val engine = FakeDownloadEngine()
        val vm = DownloadsViewModel(repo, engine)
        vm.start("t1")
        testScheduler.advanceUntilIdle()
        assertTrue(engine.started.contains("t1"))
        Unit
    }
}
