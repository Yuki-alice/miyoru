package app.miyoru.feature.localimport.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.core.common.toAppError
import app.miyoru.domain.local.model.LocalFormat
import app.miyoru.domain.local.model.LocalImportDescriptor
import app.miyoru.domain.local.model.LocalMedia
import app.miyoru.domain.local.repository.LocalImportRepository
import app.miyoru.domain.local.usecase.ImportLocalFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.write

data class ImportUiState(
    val isImporting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val lastImported: LocalMedia? = null,
)

/** 本地导入：FileKit 选文件 → 拷入 inbox → 校验 → 导入书库。 */
class ImportViewModel(
    private val filesRoot: String,
    private val local: LocalImportRepository,
    private val validate: ImportLocalFile,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun importPicked(fileName: String, bytes: ByteArray) {
        if (_uiState.value.isImporting) return
        viewModelScope.launch {
            _uiState.value = ImportUiState(isImporting = true)
            runCatching {
                val format = when (fileName.substringAfterLast(".", "").lowercase()) {
                    "txt" -> LocalFormat.TXT
                    "epub" -> LocalFormat.EPUB
                    "cbz" -> LocalFormat.CBZ
                    else -> throw app.miyoru.core.common.AppError.InvalidInput("unsupported file: $fileName")
                }
                writeInbox(fileName, bytes)
                validate(
                    LocalImportDescriptor(
                        fileName = fileName.substringAfterLast("/"),
                        format = format,
                        sizeBytes = bytes.size.toLong(),
                        relativePath = fileName.substringAfterLast("/"),
                    ),
                )
            }.onSuccess { media ->
                _uiState.value = ImportUiState(message = "已导入《${media.title}》", lastImported = media)
            }.onFailure {
                val err = try {
                    it.toAppError("import")
                } catch (e: Exception) {
                    e
                }
                _uiState.value = ImportUiState(message = err.message, isError = true)
            }
        }
    }

    private fun writeInbox(fileName: String, bytes: ByteArray) {
        val rel = fileName.substringAfterLast("/")
        val target = Path(filesRoot, "inbox", rel)
        val parent = target.parent
        if (parent != null) SystemFileSystem.createDirectories(parent)
        SystemFileSystem.sink(target).buffered().use { it.write(bytes) }
    }
}
