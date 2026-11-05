package app.miyoru.feature.statistics

import app.miyoru.core.model.MediaType
import app.miyoru.domain.download.model.DownloadTask
import app.miyoru.domain.library.model.Favorite
import app.miyoru.domain.library.model.HistoryEntry
import app.miyoru.feature.FakeDownloadRepository
import app.miyoru.feature.FakeLibraryRepository
import app.miyoru.feature.media
import app.miyoru.feature.statistics.presentation.StatisticsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    @Test
    fun `counts by type and running`() = runTest {
        val lib = FakeLibraryRepository()
        val n = media("s", MediaType.NOVEL, "r1", "N")
        val c = media("s", MediaType.COMIC, "r2", "C")
        lib.media.addAll(listOf(n, c))
        lib.favorites.value = listOf(Favorite(n.key, 1), Favorite(c.key, 2))
        lib.history.value = listOf(HistoryEntry(n.key, 1))
        val dl = FakeDownloadRepository()
        dl.tasks.value = listOf(DownloadTask("t1", "RUNNING"), DownloadTask("t2", "COMPLETED"))
        val state = StatisticsViewModel(lib, dl).uiState.first { !it.isLoading }
        assertEquals(2, state.favoritesCount)
        assertEquals(1, state.novelsCount)
        assertEquals(1, state.comicsCount)
        assertEquals(0, state.videosCount)
        assertEquals(1, state.historyCount)
        assertEquals(1, state.runningDownloads)
        Unit
    }
}
