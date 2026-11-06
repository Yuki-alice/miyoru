@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package app.miyoru.platform.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.error
import platform.AVFoundation.rate
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setRate
import platform.AVFoundation.status
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
        // 本地文件允许传裸路径；网络地址仍用 URLWithString。
        val nsUrl = if (url.startsWith("http", ignoreCase = true) || url.startsWith("file", ignoreCase = true)) {
            NSURL.URLWithString(url)
        } else {
            NSURL.fileURLWithPath(url)
        } ?: run {
            _state.value = PlaybackState(errorMessage = "bad url")
            return
        }
        val asset = if (headers.isEmpty()) {
            AVURLAsset.URLAssetWithURL(nsUrl, null)
        } else {
            AVURLAsset.URLAssetWithURL(nsUrl, mapOf<Any?, Any>("AVURLAssetHTTPHeaderFieldsKey" to headers))
        }
        val item = AVPlayerItem.playerItemWithAsset(asset)
        player.replaceCurrentItemWithPlayerItem(item)
        if (observer == null) {
            observer = player.addPeriodicTimeObserverForInterval(
                CMTimeMake(value = 1, timescale = 2),
                null,
            ) { _ ->
                scope.launch { push() }
            }
        }

        // 等资源可播或失败，避免把失败当成“已开始”。
        withTimeoutOrNull(READY_TIMEOUT_MILLIS) {
            while (item.status != AVPlayerItemStatusReadyToPlay && item.status != AVPlayerItemStatusFailed) {
                delay(100)
                push()
            }
            if (item.status == AVPlayerItemStatusFailed) {
                _state.value = _state.value.copy(errorMessage = item.error?.localizedDescription ?: "播放失败")
            }
        }
        push()
    }

    private fun push() {
        val item = player.currentItem
        if (item?.status == AVPlayerItemStatusFailed) {
            _state.value = _state.value.copy(
                isPlaying = false,
                errorMessage = item.error?.localizedDescription ?: _state.value.errorMessage,
            )
            return
        }
        val pos = CMTimeGetSeconds(player.currentTime())
        val dur = item?.let { CMTimeGetSeconds(it.duration) } ?: 0.0
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
        observer?.let { player.removeTimeObserver(it) }
        observer = null
        player.replaceCurrentItemWithPlayerItem(null)
    }

    internal fun rawPlayer(): AVPlayer = player

    private companion object {
        const val READY_TIMEOUT_MILLIS = 15_000L
    }
}
