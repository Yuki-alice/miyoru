@file:OptIn(kotlin.time.ExperimentalTime::class)
package app.miyoru.feature.videoplayer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.RemoteId
import app.miyoru.domain.library.model.MediaProgress
import app.miyoru.domain.library.usecase.UpdateProgress
import app.miyoru.domain.source.model.ContentUnit
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
    /** 选集列表，对应原型播放器右侧/下方的“选集”。 */
    val units: List<ContentUnit> = emptyList(),
    val currentUnitId: String? = null,
    val intro: String? = null,
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
            runCatching { load(unitId?.value) }.onFailure { fail(it) }
        }
        // 播放状态只订阅一次；切集复用同一条订阅，避免重复收集。
        viewModelScope.launch {
            player.state.collect { ps ->
                _uiState.value = _uiState.value.copy(
                    isPlaying = ps.isPlaying, positionMs = ps.positionMillis,
                    durationMs = ps.durationMillis,
                    // 解析阶段的失败提示不能被播放器初始的空状态覆盖
                    errorMessage = ps.errorMessage ?: _uiState.value.errorMessage,
                )
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

    /** 切集：先保存当前进度，再解析目标集。 */
    fun selectUnit(id: String) {
        if (_uiState.value.currentUnitId == id) return
        viewModelScope.launch {
            runCatching { snapshot() }
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { load(id) }.onFailure { fail(it) }
        }
    }

    fun retry() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { load(unitId?.value) }.onFailure { fail(it) }
        }
    }

    override fun onCleared() {
        runCatching { player.pause() }
        ticker?.cancel()
        super.onCleared()
    }

    private fun fail(error: Throwable) {
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.message)
    }

    private suspend fun load(targetUnitId: String?) {
        val detail = sources.detail(key)
        val units = sources.contentUnits(key)
        val target = targetUnitId?.let { id -> units.firstOrNull { it.unitId == id } } ?: units.firstOrNull()
            ?: throw IllegalStateException("no episodes")
        val resolved = sources.resolve(key, RemoteId(target.unitId))
        val stream = (resolved as? ResolvedContent.Video)?.streams?.firstOrNull()
            ?: throw IllegalStateException("no streams")
        player.prepare(stream.url, stream.headers, stream.mimeType)
        _uiState.value = PlayerUiState(
            isLoading = false,
            title = detail.summary.title,
            units = units,
            currentUnitId = target.unitId,
            intro = detail.attributes["intro"] ?: detail.attributes["description"] ?: detail.attributes["summary"],
        )
        player.play()
    }

    private suspend fun snapshot() {
        val s = _uiState.value
        if (s.durationMs <= 0) return
        try {
            updateProgress(
                MediaProgress(
                    key = key,
                    unitId = s.currentUnitId ?: "e0",
                    position = s.positionMs,
                    total = s.durationMs,
                    updatedAt = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                ),
            )
        } catch (_: Exception) {
        }
    }
}
