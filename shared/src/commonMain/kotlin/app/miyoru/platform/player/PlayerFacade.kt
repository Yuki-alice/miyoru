package app.miyoru.platform.player

import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val errorMessage: String? = null,
)

interface PlayerFacade {
    val state: StateFlow<PlaybackState>
    /** 平台播放器实例（Android=ExoPlayer），供 VideoSurface 使用；不支持的平台为 null。 */
    val nativePlayer: Any? get() = null
    suspend fun prepare(url: String, headers: Map<String, String> = emptyMap(), mimeType: String? = null)
    fun play()
    fun pause()
    fun seekTo(positionMillis: Long)
    fun setSpeed(speed: Float)
    fun release()
}
