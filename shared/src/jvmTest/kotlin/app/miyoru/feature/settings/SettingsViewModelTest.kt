package app.miyoru.feature.settings

import app.miyoru.domain.settings.repository.SettingsRepository
import app.miyoru.data.settings.SettingsRepositoryImpl
import app.miyoru.feature.settings.presentation.SettingsViewModel
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    private fun repo(name: String): SettingsRepository {
        val f = File("build/tmp/settings-$name.preferences_pb")
        f.parentFile?.mkdirs()
        f.delete()
        return SettingsRepositoryImpl(PreferenceDataStoreFactory.create(produceFile = { f }))
    }

    @Test
    fun `theme and defaults round-trip`() = runTest {
        // DataStore 写在真实 IO 线程：用条件 first 等待落盘，不用虚拟时间推进。
        val vm = SettingsViewModel(repo("t1"))
        assertEquals("system", vm.uiState.first().themeMode)
        vm.setTheme("dark")
        assertEquals("dark", vm.uiState.first { it.themeMode == "dark" }.themeMode)
        vm.setConcurrency(99)
        assertEquals(8, vm.uiState.first { it.concurrency == 8 }.concurrency)
        Unit
    }
}
