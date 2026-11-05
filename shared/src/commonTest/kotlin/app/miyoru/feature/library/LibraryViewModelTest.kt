package app.miyoru.feature.library

import app.miyoru.core.model.MediaType
import app.miyoru.domain.library.model.Favorite
import app.miyoru.feature.FakeLibraryRepository
import app.miyoru.feature.library.presentation.LibraryViewModel
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
class LibraryViewModelTest {
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
    fun `empty favorites show empty`() = runTest {
        val vm = LibraryViewModel(FakeLibraryRepository())
        val state = vm.uiState.first { !it.isLoading }
        assertTrue(state.items.isEmpty())
        Unit
    }

    @Test
    fun `filter narrows by type`() = runTest {
        val repo = FakeLibraryRepository()
        val novel = media("s", MediaType.NOVEL, "r1", "N1")
        val comic = media("s", MediaType.COMIC, "r2", "C1")
        repo.media.addAll(listOf(novel, comic))
        repo.favorites.value = listOf(Favorite(novel.key, 1), Favorite(comic.key, 2))
        val vm = LibraryViewModel(repo)
        var state = vm.uiState.first { !it.isLoading }
        assertEquals(2, state.items.size)
        vm.selectType(MediaType.COMIC)
        state = vm.uiState.first { !it.isLoading && it.selectedType == MediaType.COMIC }
        assertEquals(listOf("C1"), state.items.map { it.title })
        Unit
    }
}
