package app.miyoru.platform.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 平台视频画面：Android=PlayerView，桌面=mediamp mpv surface，iOS=AVPlayerLayer。
 * player 为平台播放器实例，由调用方从同一 PlayerFacade 来。
 */
@Composable
expect fun VideoSurface(player: Any?, modifier: Modifier = Modifier)
