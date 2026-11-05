package app.miyoru.feature.downloads.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.domain.download.model.DownloadTask
import app.miyoru.domain.download.repository.DownloadRepository
import app.miyoru.platform.download.DownloadEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val isLoading: Boolean = true,
    val tasks: List<DownloadTask> = emptyList(),
    val errorMessage: String? = null,
)

class DownloadsViewModel(
    private val repository: DownloadRepository,
    private val engine: DownloadEngine,
) : ViewModel() {
    private val running = mutableMapOf<String, Job>()

    val uiState: StateFlow<DownloadsUiState> = repository.observeTasks()
        .map<_, DownloadsUiState> { DownloadsUiState(isLoading = false, tasks = it) }
        .catch { emit(DownloadsUiState(isLoading = false, errorMessage = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    /** 恢复执行：Repository 置 QUEUED 后用引擎实际跑（Flow 收集完成）。 */
    fun start(taskId: String) {
        viewModelScope.launch {
            runCatching { repository.resume(taskId) }
            running[taskId]?.cancel()
            running[taskId] = engine.enqueue(taskId)
                .onEach { }
                .launchIn(viewModelScope)
        }
    }

    fun pause(taskId: String) {
        viewModelScope.launch {
            engine.cancel(taskId)
            running.remove(taskId)?.cancel()
            runCatching { repository.pause(taskId) }
        }
    }

    fun cancel(taskId: String) {
        viewModelScope.launch {
            engine.cancel(taskId)
            running.remove(taskId)?.cancel()
            runCatching { repository.cancel(taskId) }
        }
    }

    fun remove(taskId: String, deleteFiles: Boolean) {
        viewModelScope.launch {
            engine.cancel(taskId)
            running.remove(taskId)?.cancel()
            runCatching { repository.remove(taskId, deleteFiles) }
        }
    }
}
