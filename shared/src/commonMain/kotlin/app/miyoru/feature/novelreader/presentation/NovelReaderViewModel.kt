package app.miyoru.feature.novelreader.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.core.common.toAppError
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.RemoteId
import app.miyoru.data.local.parse.EpubParser
import app.miyoru.data.local.parse.TxtParser
import app.miyoru.domain.library.model.MediaProgress
import app.miyoru.domain.library.repository.LibraryRepository
import app.miyoru.domain.library.usecase.UpdateProgress
import app.miyoru.domain.local.repository.LocalImportRepository
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

data class ReaderChapter(val unitId: String, val title: String)

data class NovelReaderUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val text: String = "",
    val chapters: List<ReaderChapter> = emptyList(),
    val index: Int = 0,
    val errorMessage: String? = null,
)

/**
 * 小说阅读（P0）：章节粒度进度（position=章节序号，total=章节数，mode="chapter"），
 * 离开/切章即存；重进恢复到上次章节。本地书读库文件，远端走插件解析。
 */
class NovelReaderViewModel(
    private val key: MediaKey,
    private val openedUnitId: RemoteId?,
    private val sources: SourceRepository,
    private val local: LocalImportRepository,
    private val updateProgress: UpdateProgress,
    private val library: LibraryRepository,
    private val filesRoot: String,
    private val zipReader: ZipReader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NovelReaderUiState())
    val uiState: StateFlow<NovelReaderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { load() }.onFailure {
                _uiState.value = NovelReaderUiState(isLoading = false, errorMessage = it.message)
            }
        }
    }

    fun openChapter(index: Int) {
        val chapters = _uiState.value.chapters
        if (index !in chapters.indices) return
        viewModelScope.launch {
            runCatching {
                val text = loadChapterText(chapters[index].unitId)
                _uiState.value = _uiState.value.copy(text = text, index = index)
                saveProgress()
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.message)
            }
        }
    }

    fun next() = openChapter(_uiState.value.index + 1)
    fun prev() = openChapter(_uiState.value.index - 1)

    private suspend fun load() {
        val chapters: List<ReaderChapter>
        val title: String
        if (key.sourceId.value == "local") {
            val parsed = parseLocal()
            title = parsed.first
            chapters = parsed.second
        } else {
            val detail = sources.detail(key)
            title = detail.summary.title
            chapters = sources.contentUnits(key).map { ReaderChapter(it.unitId, it.title) }
        }
        val saved = library.observeProgress(key, RemoteId("__novel__")).first()
        val startIndex = openedUnitId?.value?.let { id -> chapters.indexOfFirst { it.unitId == id } }
            ?.takeIf { it >= 0 }
            ?: saved?.position?.toInt()?.takeIf { it in chapters.indices }
            ?: 0
        val text = loadChapterText(chapters.getOrNull(startIndex)?.unitId ?: "")
        _uiState.value = NovelReaderUiState(
            isLoading = false, title = title, text = text,
            chapters = chapters, index = startIndex,
        )
        saveProgress()
    }

    private suspend fun loadChapterText(unitId: String): String {
        if (key.sourceId.value == "local") {
            val bytes = readLibraryFile(key.remoteId.value)
            return when (unitId.substringAfterLast(".", "")) {
                else -> textOfLocal(bytes, key.remoteId.value, unitId)
            }
        }
        return when (val r = sources.resolve(key, RemoteId(unitId))) {
            is ResolvedContent.Novel -> r.text
            else -> throw IllegalStateException("unexpected content for novel")
        }
    }

    private suspend fun textOfLocal(bytes: ByteArray, relPath: String, unitId: String): String {
        return when (relPath.substringAfterLast(".", "").lowercase()) {
            "epub" -> {
                val book = EpubParser.parse(zipReader.read(bytes))
                book.chapters.getOrNull(unitId.removePrefix("c").toIntOrNull() ?: -1)?.text ?: ""
            }
            else -> {
                val book = TxtParser.parse(relPath.substringAfterLast("/"), bytes)
                val idx = unitId.removePrefix("c").toIntOrNull() ?: 0
                val chapters = book.chapters
                if (chapters.isEmpty()) book.text
                else {
                    val start = chapters.getOrNull(idx)?.startOffset ?: 0
                    val end = chapters.getOrNull(idx + 1)?.startOffset ?: book.text.length
                    book.text.substring(start, end)
                }
            }
        }
    }

    private suspend fun parseLocal(): Pair<String, List<ReaderChapter>> {
        val rel = key.remoteId.value
        val bytes = readLibraryFile(rel)
        return when (rel.substringAfterLast(".", "").lowercase()) {
            "epub" -> {
                val book = EpubParser.parse(zipReader.read(bytes))
                book.title to book.chapters.mapIndexed { i, c -> ReaderChapter("c$i", c.title) }
            }
            else -> {
                val book = TxtParser.parse(rel.substringAfterLast("/"), bytes)
                val list = if (book.chapters.isEmpty()) listOf(ReaderChapter("c0", book.title))
                else book.chapters.mapIndexed { i, c -> ReaderChapter("c$i", c.title) }
                book.title to list
            }
        }
    }

    private fun readLibraryFile(relativePath: String): ByteArray {
        val p = Path(filesRoot, "library", relativePath)
        return SystemFileSystem.source(p).buffered().use { it.readByteArray() }
    }

    private suspend fun saveProgress() {
        val s = _uiState.value
        if (s.chapters.isEmpty()) return
        try {
            updateProgress(
                MediaProgress(key, "__novel__", s.index.toLong(), s.chapters.size.toLong(), nowMs()),
            )
        } catch (_: Exception) {
        }
    }

    private fun nowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}
