package app.miyoru.platform.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 平台视频画面：Android=PlayerView，其他平台占位（桌面 MPV / iOS AVKit 在平台集成轮落地）。
 * player 为平台播放器实例（Android=ExoPlayer），由调用方从同一 PlayerFacade 来。
 */
@Composable
expect fun VideoSurface(player: Any?, modifier: Modifier = Modifier)
