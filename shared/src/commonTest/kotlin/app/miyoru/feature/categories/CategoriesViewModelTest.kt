package app.miyoru.feature.categories

import app.miyoru.core.model.MediaType
import app.miyoru.domain.library.model.Favorite
import app.miyoru.feature.FakeLibraryRepository
import app.miyoru.feature.categories.presentation.CategoriesViewModel
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
class CategoriesViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    @Test
    fun `counts per type`() = runTest {
        val lib = FakeLibraryRepository()
        val n = media("s", MediaType.NOVEL, "r1", "N")
        lib.media.add(n)
        lib.favorites.value = listOf(Favorite(n.key, 1))
        val entries = CategoriesViewModel(lib).uiState.first { !it.isLoading }.entries
        assertEquals(1, entries.first { it.type == MediaType.NOVEL }.count)
        assertEquals(0, entries.first { it.type == MediaType.VIDEO }.count)
        Unit
    }
}
