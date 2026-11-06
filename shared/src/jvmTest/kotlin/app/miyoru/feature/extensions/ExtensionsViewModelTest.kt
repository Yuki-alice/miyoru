package app.miyoru.feature.extensions

import app.miyoru.feature.FakePluginCatalogRepository
import app.miyoru.feature.FakePluginRepository
import app.miyoru.domain.plugin.model.CatalogEntry
import app.miyoru.domain.plugin.model.CatalogEntryState
import app.miyoru.domain.plugin.model.PluginUpdate
import app.miyoru.domain.plugin.model.RefreshOutcome
import app.miyoru.feature.extensions.presentation.ExtensionsViewModel
import app.miyoru.feature.extensions.presentation.TAB_UPDATES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ExtensionsViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    private fun entry(state: CatalogEntryState = CatalogEntryState.AVAILABLE) = CatalogEntry(
        pluginId = "demo.source",
        repositoryId = "demo.repository",
        name = "Demo Source",
        version = "1.0.0",
        mediaTypes = listOf("novel"),
        domains = listOf("https://example.com"),
        state = state,
    )

    @Test
    fun `install requires explicit permission confirmation`() = runTest {
        val catalog = FakePluginCatalogRepository()
        val vm = ExtensionsViewModel(catalog, FakePluginRepository())
        vm.requestInstall(entry())
        assertEquals(entry(), vm.uiState.value.pendingInstall)
        assertTrue(catalog.installed.isEmpty())
        vm.confirmInstall()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("demo.repository" to "demo.source"), catalog.installed)
        assertEquals(null, vm.uiState.value.pendingInstall)
        assertTrue(vm.uiState.value.message!!.contains("已安装"))
        Unit
    }

    @Test
    fun `refresh reports update count and switches tab`() = runTest {
        val catalog = FakePluginCatalogRepository()
        catalog.refreshOutcome = RefreshOutcome(refreshed = 2, updateCount = 3)
        catalog.snapshot.value = catalog.snapshot.value.copy(
            updates = listOf(PluginUpdate("demo.source", "2.0.0", "demo.repository")),
        )
        val vm = ExtensionsViewModel(catalog, FakePluginRepository())
        vm.selectTab(TAB_UPDATES)
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(TAB_UPDATES, vm.uiState.value.tab)
        assertTrue(vm.uiState.value.message!!.contains("3 个更新"))
        assertEquals(1, catalog.refreshCalls)
        Unit
    }

    @Test
    fun `fingerprint change is surfaced as an explicit warning`() = runTest {
        val catalog = FakePluginCatalogRepository()
        catalog.refreshOutcome = RefreshOutcome(fingerprintChanged = listOf("demo.repository"))
        val vm = ExtensionsViewModel(catalog, FakePluginRepository())
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isError)
        assertTrue(vm.uiState.value.message!!.contains("重新确认信任"))
        Unit
    }

    @Test
    fun `uninstall delegates to installed plugin repository`() = runTest {
        val plugins = FakePluginRepository()
        val vm = ExtensionsViewModel(FakePluginCatalogRepository(), plugins)
        vm.uninstall("demo.source")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("demo.source"), plugins.uninstalled)
        Unit
    }
}
