package app.miyoru.feature.detail

import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.domain.source.model.ContentUnit
import app.miyoru.domain.source.model.MediaDetail
import app.miyoru.feature.FakeLibraryRepository
import app.miyoru.feature.FakeSourceRepository
import app.miyoru.feature.detail.presentation.DetailViewModel
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
class DetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val key = MediaKey(SourceId("s"), MediaType.NOVEL, RemoteId("r"))
    private val summary = media("s", MediaType.NOVEL, "r", "T")

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

    private fun vm(sources: FakeSourceRepository, lib: FakeLibraryRepository) =
        DetailViewModel(key, sources, lib)

    @Test
    fun `loads detail units and favorite`() = runTest {
        val lib = FakeLibraryRepository()
        val sources = FakeSourceRepository(
            detail = MediaDetail(summary, "Src"),
            units = listOf(ContentUnit("u1", "C1", 0)),
        )
        val state = vm(sources, lib).uiState.first { !it.isLoading }
        assertEquals("T", state.detail?.summary?.title)
        assertEquals(1, state.units.size)
        assertEquals(false, state.isFavorite)
        Unit
    }

    @Test
    fun `toggle favorite flips`() = runTest {
        val lib = FakeLibraryRepository()
        val sources = FakeSourceRepository(detail = MediaDetail(summary, "Src"))
        val viewModel = vm(sources, lib)
        viewModel.uiState.first { !it.isLoading }
        viewModel.toggleFavorite()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.first { !it.isLoading }.isFavorite)
        Unit
    }

    @Test
    fun `failure shows error`() = runTest {
        val state = vm(FakeSourceRepository(fail = true), FakeLibraryRepository())
            .uiState.first { !it.isLoading }
        assertTrue(state.errorMessage != null)
        Unit
    }
}
