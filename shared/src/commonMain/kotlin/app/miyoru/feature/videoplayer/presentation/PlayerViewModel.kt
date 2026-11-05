@file:OptIn(kotlin.time.ExperimentalTime::class)
package app.miyoru.feature.videoplayer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.RemoteId
import app.miyoru.domain.library.model.MediaProgress
import app.miyoru.domain.library.usecase.UpdateProgress
import app.miyoru.domain.source.model.ResolvedContent
import app.miyoru.domain.source.repository.SourceRepository
import app.miyoru.platform.player.PlayerFacade
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val errorMessage: String? = null,
)

class PlayerViewModel(
    private val key: MediaKey,
    private val unitId: RemoteId?,
    private val sources: SourceRepository,
    private val updateProgress: UpdateProgress,
    private val player: PlayerFacade,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            runCatching { load() }.onFailure {
                _uiState.value = PlayerUiState(isLoading = false, errorMessage = it.message)
            }
        }
        // 定时快照用真实时间调度器，避免与 runTest 虚拟时间互锁；
        // 生产行为不变（5s 节奏），测试结束随 viewModelScope 取消。
        ticker = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            while (isActive) {
                delay(5_000)
                runCatching { snapshot() }
            }
        }
    }

    fun toggle() {
        val s = _uiState.value
        if (s.isPlaying) {
            player.pause()
            viewModelScope.launch { runCatching { snapshot() } }
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun retry() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { load() }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message)
            }
        }
    }

    override fun onCleared() {
        runCatching { player.pause() }
        ticker?.cancel()
        super.onCleared()
    }

    private suspend fun load() {
        val detail = sources.detail(key)
        val units = sources.contentUnits(key)
        val target = unitId?.value?.let { id -> units.firstOrNull { it.unitId == id } } ?: units.firstOrNull()
            ?: throw IllegalStateException("no episodes")
        val resolved = sources.resolve(key, RemoteId(target.unitId))
        val stream = (resolved as? ResolvedContent.Video)?.streams?.firstOrNull()
            ?: throw IllegalStateException("no streams")
        player.prepare(stream.url, stream.headers, stream.mimeType)
        _uiState.value = PlayerUiState(
            isLoading = false, title = detail.summary.title,
            isPlaying = false, positionMs = 0, durationMs = 0,
        )
        viewModelScope.launch {
            player.state.collect { ps ->
                _uiState.value = _uiState.value.copy(
                    isPlaying = ps.isPlaying, positionMs = ps.positionMillis,
                    durationMs = ps.durationMillis, errorMessage = ps.errorMessage,
                    isLoading = false,
                )
            }
        }
        player.play()
    }

    private suspend fun snapshot() {
        val s = _uiState.value
        if (s.durationMs <= 0) return
        try {
            updateProgress(
                MediaProgress(
                    key, (unitId?.value ?: "e0"), s.positionMs,
                    s.durationMs, kotlin.time.Clock.System.now().toEpochMilliseconds(),
                ),
            )
        } catch (_: Exception) {
        }
    }
}
