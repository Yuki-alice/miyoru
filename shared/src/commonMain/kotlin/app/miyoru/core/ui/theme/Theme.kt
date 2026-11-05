package app.miyoru.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 紫色基准（设计原型 paletteKey=purple，强调色 #6750A4）；深色优先，浅色同步支持。
private val LightPrimary = Color(0xFF6750A4)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFE8DEF8)

private val DarkPrimary = Color(0xFFD0BCFF)
private val DarkOnPrimary = Color(0xFF381E72)
private val DarkSecondaryContainer = Color(0xFF4A4458)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    secondaryContainer = LightSecondaryContainer,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondaryContainer = DarkSecondaryContainer,
)

/** 应用主题：跟随系统深浅色；动态取色（Android Material You）在平台验证后接入。 */
@Composable
fun MiyoruTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
