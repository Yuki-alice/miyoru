package app.miyoru.feature.novelreader

import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.domain.library.model.MediaProgress
import app.miyoru.domain.source.model.MediaDetail
import app.miyoru.feature.FakeLibraryRepository
import app.miyoru.feature.FakeLocalImportRepository
import app.miyoru.feature.FakeSourceRepository
import app.miyoru.feature.FakeZipReader
import app.miyoru.feature.media
import app.miyoru.feature.novelreader.presentation.NovelReaderViewModel
import app.miyoru.domain.library.usecase.UpdateProgress
import app.miyoru.domain.source.model.ContentUnit
import app.miyoru.domain.source.model.ResolvedContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun wipe(path: Path) {
    val meta = SystemFileSystem.metadataOrNull(path) ?: return
    if (meta.isDirectory) SystemFileSystem.list(path).forEach { wipe(it) }
    SystemFileSystem.delete(path, mustExist = false)
}

@OptIn(ExperimentalCoroutinesApi::class)
class NovelReaderViewModelTest {
    private val key = MediaKey(SourceId("s"), MediaType.NOVEL, RemoteId("r"))

    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    private fun remoteVm(sources: FakeSourceRepository, lib: FakeLibraryRepository): NovelReaderViewModel =
        NovelReaderViewModel(key, null, sources, FakeLocalImportRepository(), UpdateProgress(lib), lib, "build/tmp/novel", FakeZipReader())

    @Test
    fun `remote novel loads and saves chapter progress`() = runTest {
        val lib = FakeLibraryRepository()
        val sources = FakeSourceRepository(
            detail = MediaDetail(media("s", MediaType.NOVEL, "r", "T"), "Src"),
            units = listOf(ContentUnit("u1", "C1", 0), ContentUnit("u2", "C2", 1)),
            resolveHandler = { _, unit -> ResolvedContent.Novel("text-of-" + unit.value) },
        )
        val vm = remoteVm(sources, lib)
        var state = vm.uiState.first { !it.isLoading }
        assertEquals("text-of-u1", state.text)
        vm.next()
        testScheduler.advanceUntilIdle()
        state = vm.uiState.first { !it.isLoading }
        assertEquals(1, state.index)
        assertEquals("text-of-u2", state.text)
        val saved = lib.progress.values.single()
        assertEquals(1L, saved.position)
        Unit
    }

    @Test
    fun `local txt restores saved chapter`() = runTest {
        val root = Path("build", "tmp", "novel-local")
        wipe(root)
        SystemFileSystem.createDirectories(Path(root, "library"))
        val text = "第一章 A\n\n正文A。\n\n第二章 B\n\n正文B。\n"
        SystemFileSystem.sink(Path(root, "library", "book.txt")).buffered().use { it.write(text.encodeToByteArray()) }
        val lib = FakeLibraryRepository()
        val localKey = MediaKey(SourceId("local"), MediaType.NOVEL, RemoteId("book.txt"))
        lib.progress["$localKey" + "__novel__"] =
            MediaProgress(localKey, "__novel__", 1, 2, 1)
        val vm = NovelReaderViewModel(
            localKey, null, FakeSourceRepository(), FakeLocalImportRepository(),
            UpdateProgress(lib), lib, root.toString(), FakeZipReader(),
        )
        val state = vm.uiState.first { !it.isLoading }
        assertEquals(1, state.index)
        assertTrue(state.text.contains("正文B"))
        Unit
    }
}
