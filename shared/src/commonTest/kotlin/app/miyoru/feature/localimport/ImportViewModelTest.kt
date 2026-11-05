package app.miyoru.feature.localimport

import app.miyoru.domain.local.model.LocalFormat
import app.miyoru.domain.local.usecase.ImportLocalFile
import app.miyoru.feature.FakeLocalImportRepository
import app.miyoru.feature.localimport.presentation.ImportViewModel
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
class ImportViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    private fun vm(root: String): Pair<ImportViewModel, FakeLocalImportRepository> {
        val repo = FakeLocalImportRepository()
        return ImportViewModel(root, repo, ImportLocalFile(repo)) to repo
    }

    @Test
    fun `import txt through inbox`() = runTest {
        val (viewModel, repo) = vm("build/tmp/import-vm")
        viewModel.importPicked("book.txt", "第一章 A\n\n正文。\n".encodeToByteArray())
        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isImporting }
        assertTrue(state.message?.contains("book") == true)
        assertEquals(1, repo.media.value.size)
        assertEquals(LocalFormat.TXT, repo.media.value.single().format)
        Unit
    }

    @Test
    fun `unsupported extension rejected`() = runTest {
        val (viewModel) = vm("build/tmp/import-vm2")
        viewModel.importPicked("doc.pdf", ByteArray(10))
        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isImporting }
        assertTrue(state.isError)
        Unit
    }
}
