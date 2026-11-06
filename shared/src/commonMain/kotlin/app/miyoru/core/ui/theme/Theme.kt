package app.miyoru.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 设计原型 paletteKey=purple（M3 baseline），深色优先、浅色同步支持。
// 语义色按 M3 surface 层级铺开，避免各屏自己拼颜色。

private val Purple40 = Color(0xFF6750A4)
private val Purple80 = Color(0xFFD0BCFF)
private val PurpleGrey40 = Color(0xFF625B71)
private val PurpleGrey80 = Color(0xFFCCC2DC)
private val Pink40 = Color(0xFF7D5260)
private val Pink80 = Color(0xFFEFB8C8)

private val LightColors = lightColorScheme(
    primary = Purple40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = PurpleGrey40,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Pink40,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Purple80,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = PurpleGrey80,
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Pink80,
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFF2B8B5),
)

/** 字阶：对齐原型（22 段落标题 / 18 小标题 / 15 正文 / 13 辅助）。 */
private val MiyoruTypography = Typography(
    displaySmall = androidx.compose.ui.text.TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium),
    headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
    headlineSmall = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
)

private val MiyoruShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 布局常量：来自原型的栅格（侧栏 80 + 32 边距，4 列 16 间距）。 */
object Dimens {
    val RailWidth = 80.dp
    val TopBarHeight = 64.dp
    val PagePadding = 32.dp
    val PagePaddingCompact = 16.dp
    val Gap = 16.dp
    val SectionGap = 24.dp
    val RowHeight = 72.dp
    val CardHeightWide = 340.dp
    val CardHeightCompact = 250.dp
    val SearchHeight = 56.dp
    val TabHeight = 48.dp
    val ChipHeight = 32.dp
}

/**
 * 应用主题。设计原型为深色优先（theme.dark=true 且 bothModes=true），
 * 因此桌面上默认深色；系统为浅色时不强制反转，交由 [darkTheme] 参数控制。
 */
@Composable
fun MiyoruTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MiyoruTypography,
        shapes = MiyoruShapes,
        content = content,
    )
}

/** 供不依赖系统判断的调用方使用（例如需要跟随系统的平台入口）。 */
@Composable
fun systemDarkTheme(): Boolean = isSystemInDarkTheme()
