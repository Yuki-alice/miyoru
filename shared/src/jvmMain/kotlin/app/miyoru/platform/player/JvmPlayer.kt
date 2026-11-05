package app.miyoru.platform.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// 桌面 MPV 后端在平台集成轮落地前用占位。
actual fun createPlayerFacade(): PlayerFacade = StubPlayerFacade("desktop video coming soon (MPV backend pending)")

@Composable
actual fun VideoSurface(player: Any?, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("桌面播放画面待接入")
    }
}
