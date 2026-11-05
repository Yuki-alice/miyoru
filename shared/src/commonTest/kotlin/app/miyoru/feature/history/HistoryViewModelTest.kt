package app.miyoru.feature.history

import app.miyoru.core.model.MediaType
import app.miyoru.domain.library.model.HistoryEntry
import app.miyoru.feature.FakeLibraryRepository
import app.miyoru.feature.history.presentation.HistoryViewModel
import app.miyoru.feature.media
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
        Unit
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
        Unit
    }

    @Test
    fun `history lists and removes`() = runTest {
        val repo = FakeLibraryRepository()
        val m = media("s", MediaType.NOVEL, "r1", "T1")
        repo.media.add(m)
        repo.history.value = listOf(HistoryEntry(m.key, 5))
        val vm = HistoryViewModel(repo)
        var state = vm.uiState.first { !it.isLoading }
        assertEquals(listOf("T1"), state.items.map { it.title })
        vm.remove(m.key)
        testScheduler.advanceUntilIdle()
        state = vm.uiState.first { !it.isLoading }
        assertTrue(state.items.isEmpty())
        Unit
    }
}
