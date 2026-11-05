package app.miyoru.feature.storage

import app.miyoru.feature.FakeCacheRepository
import app.miyoru.feature.storage.presentation.StorageViewModel
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
class StorageViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    @Test
    fun `shows size and clears`() = runTest {
        val repo = FakeCacheRepository()
        repo.bytes.value = 2048
        val vm = StorageViewModel(repo)
        assertEquals(2048L, vm.uiState.first { !it.isLoading }.cacheBytes)
        vm.clearCache()
        testScheduler.advanceUntilIdle()
        assertEquals(1, repo.cleared)
        Unit
    }
}
