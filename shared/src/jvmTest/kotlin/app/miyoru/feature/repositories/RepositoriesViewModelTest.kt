package app.miyoru.feature.repositories

import app.miyoru.feature.FakePluginCatalogRepository
import app.miyoru.feature.FakePluginRepository
import app.miyoru.domain.plugin.model.InstalledPlugin
import app.miyoru.domain.plugin.model.PluginRepo
import app.miyoru.domain.plugin.model.RefreshOutcome
import app.miyoru.feature.repositories.presentation.RepositoriesViewModel
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoriesViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    private fun repo() = PluginRepo(
        id = "demo.repository", url = "https://example.com/index.json",
        name = "Demo", schemaVersion = 1, fingerprint = "f".repeat(64),
    )

    @Test
    fun `inspect then confirm trust adds repository`() = runTest {
        val catalog = FakePluginCatalogRepository()
        val vm = RepositoriesViewModel(catalog, FakePluginRepository())
        vm.showAddDialog()
        vm.updateUrl("https://example.com/index.json")
        vm.inspect()
        testScheduler.advanceUntilIdle()
        val preview = vm.uiState.value.preview
        assertNotNull(preview)
        assertEquals(listOf("https://example.com/index.json"), catalog.inspected)
        assertTrue(catalog.addedFingerprints.isEmpty())

        vm.confirmAdd()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(preview.fingerprint), catalog.addedFingerprints)
        assertEquals(false, vm.uiState.value.showAddDialog)
        assertTrue(vm.uiState.value.message!!.contains("已信任并添加"))
        Unit
    }

    @Test
    fun `inspect failure shows a dialog error and never persists`() = runTest {
        val catalog = FakePluginCatalogRepository()
        catalog.inspectError = "bad index"
        val vm = RepositoriesViewModel(catalog, FakePluginRepository())
        vm.updateUrl("https://example.com/index.json")
        vm.inspect()
        testScheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.preview)
        assertTrue(vm.uiState.value.dialogError!!.contains("不合法"))
        assertTrue(catalog.addedFingerprints.isEmpty())
        Unit
    }

    @Test
    fun `delete shows impact count and honors removePlugins choice`() = runTest {
        val catalog = FakePluginCatalogRepository()
        val plugins = FakePluginRepository()
        plugins.installed.value = listOf(
            InstalledPlugin("p1", "1.0.0", true, "demo.repository"),
            InstalledPlugin("p2", "1.0.0", true, "demo.repository"),
        )
        val vm = RepositoriesViewModel(catalog, plugins)
        testScheduler.advanceUntilIdle()
        vm.requestDelete(repo())
        assertEquals(2, vm.uiState.value.deletePluginCount)
        vm.confirmDelete(removePlugins = true)
        testScheduler.advanceUntilIdle()
        assertEquals("demo.repository" to true, catalog.removed)
        assertNull(vm.uiState.value.pendingDelete)
        Unit
    }

    @Test
    fun `refresh reports fingerprint changes`() = runTest {
        val catalog = FakePluginCatalogRepository()
        catalog.refreshOutcome = RefreshOutcome(
            refreshed = 1,
            fingerprintChanged = listOf("demo.repository"),
        )
        val vm = RepositoriesViewModel(catalog, FakePluginRepository())
        vm.refreshAll()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.message!!.contains("重新识别并确认信任"))
        Unit
    }
}
