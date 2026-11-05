package app.miyoru.feature.browse

import app.miyoru.feature.FakeSourceRepository
import app.miyoru.feature.browse.presentation.BrowseViewModel
import app.miyoru.feature.sourceInfo
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
class BrowseViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    @Test
    fun `lists subscribed sources`() = runTest {
        val vm = BrowseViewModel(FakeSourceRepository(sources = listOf(sourceInfo("a"), sourceInfo("b"))))
        val state = vm.uiState.first { !it.isLoading }
        assertEquals(listOf("a", "b"), state.sources.map { it.id.value })
        Unit
    }
}
