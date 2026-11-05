package app.miyoru.feature.comicreader

import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.domain.source.model.ContentUnit
import app.miyoru.feature.FakeLibraryRepository
import app.miyoru.feature.FakeLocalImportRepository
import app.miyoru.feature.FakeSourceRepository
import app.miyoru.feature.FakeZipReader
import app.miyoru.feature.comicreader.presentation.ComicReaderViewModel
import app.miyoru.feature.media
import app.miyoru.domain.library.usecase.UpdateProgress
import app.miyoru.domain.source.model.MediaDetail
import app.miyoru.domain.source.model.ResolvedContent
import app.miyoru.domain.source.model.ImageResource
import app.miyoru.plugin.api.PackageEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.buffered
import kotlinx.io.write
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ComicReaderViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    @Test
    fun `remote comic pages load with headers`() = runTest {
        val lib = FakeLibraryRepository()
        val sources = FakeSourceRepository(
            detail = MediaDetail(media("s", MediaType.COMIC, "r", "C"), "Src"),
            units = listOf(ContentUnit("ch1", "话1", 0)),
            resolveHandler = { _, _ ->
                ResolvedContent.Comic(
                    listOf(
                        ImageResource("https://x/1.jpg", mapOf("Referer" to "https://x/")),
                        ImageResource("https://x/2.jpg"),
                    ),
                )
            },
        )
        val key = MediaKey(SourceId("s"), MediaType.COMIC, RemoteId("r"))
        val vm = ComicReaderViewModel(key, null, sources, FakeLocalImportRepository(), UpdateProgress(lib), lib, "build/tmp", FakeZipReader())
        var state = vm.uiState.first { !it.isLoading }
        assertEquals(2, state.pages.size)
        assertEquals("https://x/1.jpg", state.pages[0].url)
        assertEquals("https://x/", state.pages[0].headers["Referer"])
        vm.setPage(1)
        testScheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.first { !it.isLoading }.pageIndex)
        val saved = lib.progress.values.single()
        assertEquals(1L, saved.position)
        Unit
    }

    @Test
    fun `local cbz reads bytes from library`() = runTest {
        val root = kotlinx.io.files.Path("build", "tmp", "comic-local")
        val libDir = kotlinx.io.files.Path(root, "library")
        kotlinx.io.files.SystemFileSystem.createDirectories(libDir)
        kotlinx.io.files.SystemFileSystem.sink(kotlinx.io.files.Path(libDir, "c.cbz")).buffered().use { it.write(byteArrayOf(9)) }
        val lib = FakeLibraryRepository()
        val zip = FakeZipReader(
            listOf(PackageEntry("001.jpg", byteArrayOf(1, 2)), PackageEntry("002.jpg", byteArrayOf(3))),
        )
        val key = MediaKey(SourceId("local"), MediaType.COMIC, RemoteId("c.cbz"))
        val vm = ComicReaderViewModel(key, null, FakeSourceRepository(), FakeLocalImportRepository(), UpdateProgress(lib), lib, root.toString(), zip)
        val state = vm.uiState.first { !it.isLoading }
        assertEquals(2, state.pages.size)
        assertEquals(listOf<Byte>(1, 2), state.pages[0].bytes?.toList())
        Unit
    }
}
