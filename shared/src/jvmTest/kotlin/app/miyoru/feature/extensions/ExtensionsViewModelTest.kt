package app.miyoru.feature.extensions

import app.miyoru.data.plugin.InstallPluginFromUrl
import app.miyoru.feature.extensions.presentation.ExtensionsViewModel
import app.miyoru.plugin.api.InstalledPackage
import app.miyoru.plugin.api.PluginInstallerFacade
import app.miyoru.plugin.api.PluginManifest
import app.miyoru.plugin.api.PluginPackageSource
import app.miyoru.plugin.api.PluginPermissions
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
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

private class FakeInstaller(val fail: Boolean = false) : PluginInstallerFacade {
    var lastUrl: String? = null
    var lastBytes: Int = 0
    override suspend fun install(pkg: PluginPackageSource, enforceSignature: Boolean): InstalledPackage {
        lastBytes = pkg.bytes.size
        if (fail) throw RuntimeException("bad zip")
        return InstalledPackage(
            PluginManifest("demo.plugin", "Demo", "1.0.0", 1, "0.1.0", mediaTypes = listOf("novel")),
            "abc", false,
        )
    }
    override suspend fun rollback(pluginId: String) = Unit
    override suspend fun uninstall(pluginId: String) = Unit
    override fun installDir(pluginId: String) = "plugins/$pluginId"
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionsViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    private fun client(bytes: ByteArray = "ZIP".encodeToByteArray()) = HttpClient(MockEngine) {
        engine { addHandler { respond(bytes, HttpStatusCode.OK) } }
    }

    @Test
    fun `install from url succeeds`() = runTest {
        val installer = FakeInstaller()
        val vm = ExtensionsViewModel(InstallPluginFromUrl(client(), installer))
        vm.updateUrl("https://example.com/p.zip")
        vm.install()
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.first { !it.isInstalling }
        assertTrue(state.message?.contains("demo.plugin") == true)
        assertEquals(3, installer.lastBytes)
        Unit
    }

    @Test
    fun `install failure shows error`() = runTest {
        val vm = ExtensionsViewModel(InstallPluginFromUrl(client(), FakeInstaller(fail = true)))
        vm.updateUrl("https://example.com/p.zip")
        vm.install()
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.first { !it.isInstalling }
        assertTrue(state.isError)
        Unit
    }
}
