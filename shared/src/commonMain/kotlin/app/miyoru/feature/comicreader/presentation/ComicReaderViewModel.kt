@file:OptIn(kotlin.time.ExperimentalTime::class)
package app.miyoru.feature.comicreader.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.RemoteId
import app.miyoru.data.local.parse.CbzParser
import app.miyoru.domain.library.model.MediaProgress
import app.miyoru.domain.library.repository.LibraryRepository
import app.miyoru.domain.library.usecase.UpdateProgress
import app.miyoru.domain.local.repository.LocalImportRepository
import app.miyoru.domain.source.model.ImageResource
import app.miyoru.domain.source.model.ResolvedContent
import app.miyoru.domain.source.repository.SourceRepository
import app.miyoru.plugin.api.ZipReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

data class ComicPage(
    val index: Int,
    val url: String? = null,
    val bytes: ByteArray? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class ComicReaderUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val chapters: List<ComicChapter> = emptyList(),
    val chapterIndex: Int = 0,
    val pages: List<ComicPage> = emptyList(),
    val pageIndex: Int = 0,
    val errorMessage: String? = null,
)

data class ComicChapter(val unitId: String, val title: String)

class ComicReaderViewModel(
    private val key: MediaKey,
    openedUnitId: RemoteId?,
    private val sources: SourceRepository,
    private val local: LocalImportRepository,
    private val updateProgress: UpdateProgress,
    private val library: LibraryRepository,
    private val filesRoot: String,
    private val zipReader: ZipReader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ComicReaderUiState())
    val uiState: StateFlow<ComicReaderUiState> = _uiState.asStateFlow()
    private val openedId: String? = openedUnitId?.value

    init {
        viewModelScope.launch {
            runCatching { load() }.onFailure {
                _uiState.value = ComicReaderUiState(isLoading = false, errorMessage = it.message)
            }
        }
    }

    fun openChapter(index: Int) {
        val chapters = _uiState.value.chapters
        if (index !in chapters.indices) return
        viewModelScope.launch {
            runCatching {
                val pages = loadPages(chapters[index].unitId)
                _uiState.value = _uiState.value.copy(chapterIndex = index, pages = pages, pageIndex = 0)
                saveProgress()
            }.onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) }
        }
    }

    fun setPage(index: Int) {
        val pages = _uiState.value.pages
        if (index !in pages.indices) return
        _uiState.value = _uiState.value.copy(pageIndex = index)
        viewModelScope.launch { runCatching { saveProgress() } }
    }

    fun nextPage() = setPage(_uiState.value.pageIndex + 1)
    fun prevPage() = setPage(_uiState.value.pageIndex - 1)

    private suspend fun load() {
        val chapters: List<ComicChapter>
        val title: String
        if (key.sourceId.value == "local") {
            title = key.remoteId.value.substringAfterLast("/")
            chapters = listOf(ComicChapter("c0", title))
        } else {
            val detail = sources.detail(key)
            title = detail.summary.title
            chapters = sources.contentUnits(key).map { ComicChapter(it.unitId, it.title) }
            if (chapters.isEmpty()) throw IllegalStateException("no chapters")
        }
        val startChapter = openedId?.let { id -> chapters.indexOfFirst { it.unitId == id } }
            .takeIf { it != null && it >= 0 } ?: 0
        val pages = loadPages(chapters[startChapter].unitId)
        val savedPage = library.observeProgress(key, RemoteId(chapters[startChapter].unitId)).first()
        _uiState.value = ComicReaderUiState(
            isLoading = false, title = title, chapters = chapters,
            chapterIndex = startChapter, pages = pages,
            pageIndex = savedPage?.position?.toInt()?.takeIf { it in pages.indices } ?: 0,
        )
        saveProgress()
    }

    private suspend fun loadPages(chapterUnitId: String): List<ComicPage> {
        if (key.sourceId.value == "local") {
            val bytes = SystemFileSystem.source(Path(filesRoot, "library", key.remoteId.value))
                .buffered().use { it.readByteArray() }
            return CbzParser.pages(zipReader.read(bytes)).mapIndexed { i, p ->
                val entry = zipReader.read(bytes).first { it.path == p.name }
                ComicPage(i, bytes = entry.bytes)
            }
        }
        return when (val r = sources.resolve(key, RemoteId(chapterUnitId))) {
            is ResolvedContent.Comic -> r.pages.mapIndexed { i, p: ImageResource ->
                ComicPage(i, url = p.url, headers = p.headers)
            }
            else -> throw IllegalStateException("unexpected content for comic")
        }
    }

    private suspend fun saveProgress() {
        val s = _uiState.value
        if (s.chapters.isEmpty() || s.pages.isEmpty()) return
        try {
            updateProgress(
                MediaProgress(
                    key, s.chapters[s.chapterIndex].unitId,
                    s.pageIndex.toLong(), s.pages.size.toLong(), nowMs(),
                ),
            )
        } catch (_: Exception) {
        }
    }

    private fun nowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}
