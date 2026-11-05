package app.miyoru.feature.sources

import app.miyoru.domain.plugin.model.InstalledPlugin
import app.miyoru.feature.FakePluginRepository
import app.miyoru.feature.sources.presentation.SourcesViewModel
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
class SourcesViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    @Test
    fun `toggle and uninstall`() = runTest {
        val repo = FakePluginRepository()
        repo.installed.value = listOf(InstalledPlugin("p1", "1.0", true))
        val vm = SourcesViewModel(repo)
        assertEquals(1, vm.uiState.first { !it.isLoading }.plugins.size)
        vm.setEnabled("p1", false)
        testScheduler.advanceUntilIdle()
        assertEquals(false, vm.uiState.first { !it.isLoading }.plugins.single().enabled)
        vm.uninstall("p1")
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.first { !it.isLoading }.plugins.isEmpty())
        Unit
    }
}
