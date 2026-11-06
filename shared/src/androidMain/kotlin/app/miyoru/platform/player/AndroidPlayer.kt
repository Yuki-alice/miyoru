package app.miyoru.platform.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** 由 androidApp 入口赋值（Application Context）。 */
object AndroidAppContext {
    lateinit var app: Context
}

actual fun createPlayerFacade(): PlayerFacade = AndroidPlayerFacade(AndroidAppContext.app)

class AndroidPlayerFacade(context: Context) : PlayerFacade {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override val nativePlayer: Any?
        get() = exoPlayer

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = push()
            override fun onPlaybackStateChanged(playbackState: Int) = push()
            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    errorMessage = error.message ?: "playback error",
                )
            }
        })
    }

    private fun push() {
        val p = exoPlayer
        _state.value = _state.value.copy(
            isPlaying = p.isPlaying,
            positionMillis = p.currentPosition.coerceAtLeast(0),
            // duration 在 READY 前是 TIME_UNSET/0，不要用它覆盖已知时长
            durationMillis = p.duration.takeIf { it > 0 } ?: _state.value.durationMillis,
        )
    }

    @OptIn(UnstableApi::class)
    override suspend fun prepare(url: String, headers: Map<String, String>, mimeType: String?) {
        _state.value = PlaybackState()
        val httpFactory = DefaultHttpDataSource.Factory().apply {
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
        }
        // DefaultDataSource 统一处理 http(s)、file://、content:// 与 asset，本地文件也能直接播。
        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { mimeType?.let { setMimeType(it) } }
            .build()
        val isHls = mimeType?.contains("mpegurl") == true ||
            url.substringBefore("?").lowercase().endsWith(".m3u8")
        val source = if (isHls) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        }
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()

        // 等首帧可播（或失败），让“加载中”真实反映起播准备情况。
        withTimeoutOrNull(READY_TIMEOUT_MILLIS) {
            while (
                exoPlayer.playbackState != Player.STATE_READY &&
                exoPlayer.playbackState != Player.STATE_ENDED &&
                _state.value.errorMessage == null
            ) {
                delay(100)
            }
        }
        push()
    }

    override fun play() { exoPlayer.play() }
    override fun pause() { exoPlayer.pause() }
    override fun seekTo(positionMillis: Long) { exoPlayer.seekTo(positionMillis) }
    override fun setSpeed(speed: Float) { exoPlayer.setPlaybackSpeed(speed) }
    override fun release() { exoPlayer.release() }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 15_000L
    }
}

@Composable
actual fun VideoSurface(player: Any?, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player as? ExoPlayer
            }
        },
        update = { view -> view.player = player as? ExoPlayer },
        modifier = modifier,
    )
}
