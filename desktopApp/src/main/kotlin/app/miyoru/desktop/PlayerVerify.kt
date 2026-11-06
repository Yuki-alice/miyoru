package app.miyoru.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.miyoru.platform.player.VideoSurface
import app.miyoru.platform.player.createPlayerFacade
import kotlinx.coroutines.delay
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.system.exitProcess

/**
 * 播放器真机验证入口（开发工具，不参与正式 UI）：
 * 用与 App 完全相同的 [app.miyoru.platform.player.PlayerFacade] 和 VideoSurface 播放给定视频，
 * 记录状态推进并把窗口截图写入文件，用于验收本地文件与网络流。
 *
 * 用法：./gradlew :desktopApp:playerVerify -Pplayer.url=<路径或 URL> -Pplayer.shot=<png 路径> -Pplayer.wait=12000
 */
fun main(args: Array<String>) = application {
    val source = args.getOrNull(0) ?: error("missing player source")
    val shotPath = args.getOrNull(1) ?: "player-verify.png"
    val waitMillis = args.getOrNull(2)?.toLongOrNull() ?: 12_000L
    val expectNetwork = source.startsWith("http")

    Window(
        onCloseRequest = { exitProcess(0) },
        title = "miyoru player verify",
        state = rememberWindowState(size = DpSize(960.dp, 600.dp)),
        alwaysOnTop = true,
    ) {
        val facade = remember { createPlayerFacade() }
        val state by facade.state.collectAsState()

        LaunchedEffect(Unit) {
            println("[verify] source=$source")
            val startedAt = System.currentTimeMillis()
            facade.prepare(source)
            facade.play()

            var maxPosition = 0L
            var maxDuration = 0L
            var captured = false
            while (System.currentTimeMillis() - startedAt < waitMillis) {
                delay(200)
                val s = facade.state.value
                maxPosition = max(maxPosition, s.positionMillis)
                maxDuration = max(maxDuration, s.durationMillis)
                println(
                    "[verify] t=${System.currentTimeMillis() - startedAt}ms " +
                        "playing=${s.isPlaying} pos=${s.positionMillis} dur=${s.durationMillis} err=${s.errorMessage}",
                )
                if (!captured && s.isPlaying && s.positionMillis >= 1_000) {
                    delay(300)
                    capture(shotPath)
                    captured = true
                    println("[verify] screenshot saved: $shotPath")
                }
            }

            val ok = maxPosition >= 2_000 && maxDuration > 0 && state.errorMessage == null
            println("[verify] RESULT ok=$ok maxPosition=$maxPosition maxDuration=$maxDuration")
            exitProcess(if (ok) 0 else 2)
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(player = facade.nativePlayer, modifier = Modifier.fillMaxSize())
            Column(
                Modifier.align(Alignment.BottomStart)
                    .background(Color(0xCC000000))
                    .padding(12.dp),
            ) {
                Text(source, color = Color.White)
                Text(
                    "playing=${state.isPlaying} pos=${state.positionMillis} " +
                        "dur=${state.durationMillis} err=${state.errorMessage}",
                    color = if (state.errorMessage == null) Color.White else Color(0xFFFF8080),
                )
            }
        }
    }
}

private fun capture(path: String) {
    val frame = java.awt.Window.getWindows()
        .filter { it.isVisible }
        .maxByOrNull { it.width * it.height }
        ?: return
    val bounds = Rectangle(frame.x, frame.y, frame.width, frame.height)
    val image = Robot().createScreenCapture(bounds)
    File(path).parentFile?.mkdirs()
    ImageIO.write(image, "png", File(path))
}
