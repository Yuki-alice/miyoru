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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 由 androidApp 入口赋值（Application Context）。 */
object AndroidAppContext {
    lateinit var app: Context
}

actual fun createPlayerFacade(): PlayerFacade = AndroidPlayerFacade(AndroidAppContext.app)

class AndroidPlayerFacade(context: Context) : PlayerFacade {
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override val nativePlayer: Any?
        get() = exoPlayer

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = push()
            override fun onPlaybackStateChanged(playbackState: Int) = push()
            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(errorMessage = error.message ?: "playback error")
            }
        })
    }

    private fun push() {
        val p = exoPlayer
        _state.value = _state.value.copy(
            isPlaying = p.isPlaying,
            positionMillis = p.currentPosition.coerceAtLeast(0),
            durationMillis = p.duration.takeIf { it > 0 } ?: 0L,
            errorMessage = null,
        )
    }

    @OptIn(UnstableApi::class)
    override suspend fun prepare(url: String, headers: Map<String, String>, mimeType: String?) {
        _state.value = PlaybackState()
        val factory = DefaultHttpDataSource.Factory().apply {
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
        }
        val item = MediaItem.fromUri(url)
        val isHls = mimeType?.contains("mpegurl") == true ||
            url.substringBefore("?").lowercase().endsWith(".m3u8")
        val source = if (isHls) HlsMediaSource.Factory(factory).createMediaSource(item)
        else ProgressiveMediaSource.Factory(factory).createMediaSource(item)
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
    }

    override fun play() { exoPlayer.play() }
    override fun pause() { exoPlayer.pause() }
    override fun seekTo(positionMillis: Long) { exoPlayer.seekTo(positionMillis) }
    override fun setSpeed(speed: Float) { exoPlayer.setPlaybackSpeed(speed) }
    override fun release() { exoPlayer.release() }
}

@Composable
actual fun VideoSurface(player: Any?, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player as? ExoPlayer
                useController = false
            }
        },
        modifier = modifier,
    )
}
