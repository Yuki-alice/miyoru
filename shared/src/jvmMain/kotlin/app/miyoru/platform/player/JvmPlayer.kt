package app.miyoru.platform.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface
import org.openani.mediamp.source.UriMediaData

/**
 * 桌面端播放后端：mediamp + mpv（与参考项目 animeko 相同的方案）。
 *
 * mediamp 提供统一的播放器状态机（state / currentPositionMillis / mediaProperties / events），
 * mpv 负责解码与渲染，原生库由 `mediamp-mpv-runtime-<os>` 在启动时从 classpath 解压加载。
 * 支持本地文件路径、`file://`、HTTP(S) 直链与 HLS(m3u8)，请求头通过 [UriMediaData] 透传给 mpv。
 */
actual fun createPlayerFacade(): PlayerFacade = JvmMpvPlayerFacade()

class JvmMpvPlayerFacade : PlayerFacade {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var mpv: MpvMediampPlayer? = null

    // 组合阶段就创建播放器，确保 MpvMediampPlayerSurface 先挂载渲染上下文，
    // 再调用 prepare；否则 mpv 会以 vo=null 起播，导致无节流地直接冲到片尾。
    override val nativePlayer: Any?
        get() = ensurePlayer()

    private fun ensurePlayer(): MpvMediampPlayer {
        mpv?.let { return it }
        MpvMediampPlayer.prepareLibraries()
        MPVHandle.setLogHandler { msg -> println("[mpv/${msg.prefix}] ${msg.line}") }
        val created = MpvMediampPlayer(Any(), scope.coroutineContext)
        mpv = created

        scope.launch {
            created.state.collect { s ->
                val error = (s.mediaStatus as? MediaStatus.Error)?.error
                _state.value = _state.value.copy(
                    isPlaying = s.isPlaying,
                    errorMessage = error?.let { it.message ?: it.toString() },
                )
            }
        }
        scope.launch {
            created.currentPositionMillis.collect { pos ->
                _state.value = _state.value.copy(positionMillis = pos.coerceAtLeast(0))
            }
        }
        scope.launch {
            created.mediaProperties.collect { props ->
                props?.durationMillis?.let { duration ->
                    _state.value = _state.value.copy(durationMillis = duration)
                }
            }
        }
        return created
    }

    override suspend fun prepare(url: String, headers: Map<String, String>, mimeType: String?) {
        val player = ensurePlayer()
        _state.value = PlaybackState()

        val result = runCatching {
            player.setMediaData(UriMediaData(url, headers), playWhenReady = false)
        }
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            _state.value = _state.value.copy(errorMessage = error?.message ?: "无法加载视频")
            return
        }

        // 等待 demuxer 就绪或失败，避免 UI 在真正可播前撤掉“加载中”。
        withTimeoutOrNull(READY_TIMEOUT_MILLIS) {
            player.state.first {
                it.mediaStatus is MediaStatus.Ready ||
                    it.mediaStatus is MediaStatus.Ended ||
                    it.mediaStatus is MediaStatus.Error
            }
        }
        pushSnapshot(player)
    }

    private fun pushSnapshot(player: MpvMediampPlayer) {
        val s = player.state.value
        val error = (s.mediaStatus as? MediaStatus.Error)?.error
        _state.value = _state.value.copy(
            isPlaying = s.isPlaying,
            positionMillis = player.currentPositionMillis.value.coerceAtLeast(0),
            durationMillis = player.mediaProperties.value?.durationMillis ?: _state.value.durationMillis,
            errorMessage = error?.let { it.message ?: it.toString() },
        )
    }

    override fun play() {
        mpv?.play()
    }

    override fun pause() {
        mpv?.pause()
    }

    override fun seekTo(positionMillis: Long) {
        mpv?.seekTo(positionMillis)
    }

    override fun setSpeed(speed: Float) {
        mpv?.features?.get(PlaybackSpeed.Key)?.set(speed)
    }

    override fun release() {
        _state.value = PlaybackState()
        mpv?.close()
        mpv = null
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 30_000L
    }
}

@Composable
actual fun VideoSurface(player: Any?, modifier: Modifier) {
    val mpv = player as? MpvMediampPlayer
    if (mpv == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("无视频") }
        return
    }
    MpvMediampPlayerSurface(mpv, modifier)
}
