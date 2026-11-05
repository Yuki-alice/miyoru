package app.miyoru.platform.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 桌面/iOS 占位：prepare 即报错，由平台集成轮替换。 */
class StubPlayerFacade(private val message: String = "video not supported on this platform yet") : PlayerFacade {
    private val _state = MutableStateFlow(PlaybackState(errorMessage = null))
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    override suspend fun prepare(url: String, headers: Map<String, String>, mimeType: String?) {
        _state.value = PlaybackState(errorMessage = message)
    }
    override fun play() = Unit
    override fun pause() = Unit
    override fun seekTo(positionMillis: Long) = Unit
    override fun setSpeed(speed: Float) = Unit
    override fun release() = Unit
}
