package app.miyoru.feature.home

import app.miyoru.core.model.MediaType
import app.miyoru.domain.library.model.HistoryEntry
import app.miyoru.feature.FakeLibraryRepository
import app.miyoru.feature.home.presentation.HomeViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    @Test
    fun `history feeds continue reading`() = runTest {
        val repo = FakeLibraryRepository()
        val m = media("s", app.miyoru.core.model.MediaType.NOVEL, "r1", "T1")
        repo.media.add(m)
        repo.history.value = listOf(HistoryEntry(m.key, 5))
        val state = HomeViewModel(repo).uiState.first { !it.isLoading }
        assertEquals(listOf("T1"), state.continueReading.map { it.title })
        assertEquals(0, state.favoritesCount)
        Unit
    }
}
