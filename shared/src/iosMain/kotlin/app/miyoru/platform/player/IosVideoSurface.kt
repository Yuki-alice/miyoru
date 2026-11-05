package app.miyoru.platform.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.QuartzCore.CATransform3DIdentity
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.UIKit.bounds

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoSurface(player: Any?, modifier: Modifier) {
    val avPlayer = player as? AVPlayer
    if (avPlayer == null) {
        Box(modifier) { Text("无视频") }
        return
    }
    val container = remember {
        object : UIView(frame = CGRectMake(0.0, 0.0, 10.0, 10.0)) {
            override fun layoutSubviews() {
                super.layoutSubviews()
                subviews.forEach { (it as UIView).setFrame(bounds) }
                (layer.sublayers?.firstOrNull() as? AVPlayerLayer)?.setFrame(bounds)
            }
        }
    }
    UIKitView(
        factory = {
            val layer = AVPlayerLayer.playerLayerWithPlayer(avPlayer)
            layer.frame = container.bounds
            container.layer.addSublayer(layer)
            container
        },
        modifier = modifier,
        update = { view ->
            (view.layer.sublayers?.firstOrNull() as? AVPlayerLayer)?.setPlayer(avPlayer)
        },
    )
}
