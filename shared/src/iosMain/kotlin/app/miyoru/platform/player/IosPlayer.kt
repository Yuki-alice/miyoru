@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package app.miyoru.platform.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.rate
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setRate
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL

actual fun createPlayerFacade(): PlayerFacade = IosAvPlayer()

class IosAvPlayer : PlayerFacade {
    private val player = AVPlayer()
    private val scope: CoroutineScope = MainScope()
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private var observer: Any? = null

    override suspend fun prepare(url: String, headers: Map<String, String>, mimeType: String?) {
        _state.value = PlaybackState()
        val nsUrl = NSURL.URLWithString(url) ?: run {
            _state.value = PlaybackState(errorMessage = "bad url")
            return
        }
        val asset = if (headers.isEmpty()) {
            AVURLAsset.URLAssetWithURL(nsUrl, null)
        } else {
            AVURLAsset.URLAssetWithURL(nsUrl, mapOf<Any?, Any>("AVURLAssetHTTPHeaderFieldsKey" to headers))
        }
        player.replaceCurrentItemWithPlayerItem(AVPlayerItem.playerItemWithAsset(asset))
        if (observer == null) {
            observer = player.addPeriodicTimeObserverForInterval(
                CMTimeMake(value = 1, timescale = 2),
                null,
            ) { _ ->
                scope.launch { push() }
            }
        }
        player.play()
        push()
    }

    private fun push() {
        val pos = CMTimeGetSeconds(player.currentTime())
        val dur = player.currentItem?.let { CMTimeGetSeconds(it.duration) } ?: 0.0
        _state.value = PlaybackState(
            isPlaying = player.rate != 0f,
            positionMillis = if (pos.isNaN()) 0 else (pos * 1000).toLong(),
            durationMillis = if (dur.isNaN() || dur.isInfinite()) 0 else (dur * 1000).toLong(),
        )
    }

    override fun play() {
        player.play()
        scope.launch { push() }
    }

    override fun pause() {
        player.pause()
        scope.launch { push() }
    }

    override fun seekTo(positionMillis: Long) {
        player.seekToTime(CMTimeMake(value = positionMillis, timescale = 1000))
    }

    override fun setSpeed(speed: Float) {
        player.setRate(speed)
    }

    override fun release() {
        player.pause()
        (observer as? Any)?.let { /* token kept for session lifetime */ }
        player.replaceCurrentItemWithPlayerItem(null)
    }

    internal fun rawPlayer(): AVPlayer = player
}
