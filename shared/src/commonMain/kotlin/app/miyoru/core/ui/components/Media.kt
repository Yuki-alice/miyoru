package app.miyoru.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.miyoru.core.model.MediaType
import app.miyoru.core.ui.icon.MiyoruIcons
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

/** 媒体类型 → 图标与短标签。 */
fun MediaType.icon(): ImageVector = when (this) {
    MediaType.COMIC -> MiyoruIcons.Comic
    MediaType.NOVEL -> MiyoruIcons.Novel
    MediaType.VIDEO -> MiyoruIcons.Video
}

fun MediaType.label(): String = when (this) {
    MediaType.COMIC -> "漫画"
    MediaType.NOVEL -> "小说"
    MediaType.VIDEO -> "视频"
}

/**
 * 封面：有 URL 用 Coil 裁切填充，无 URL 用类型图标占位（原型中的 “封面待加载” 形态）。
 */
@Composable
fun Cover(
    url: String?,
    type: MediaType?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    if (url.isNullOrBlank()) {
        // 无封面：用类型色做底，避免大面积灰块，也提示这是占位而不是空白
        val base = when (type) {
            MediaType.COMIC -> Color(0xFF3A3054)
            MediaType.NOVEL -> Color(0xFF2C3B4E)
            MediaType.VIDEO -> Color(0xFF4A2F32)
            null -> colors.surfaceContainerHigh
        }
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(listOf(base, colors.surfaceContainerLow)),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = type?.icon() ?: MiyoruIcons.Comic,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.38f),
                modifier = Modifier.size(32.dp),
            )
        }
    } else {
        val context = LocalPlatformContext.current
        AsyncImage(
            model = ImageRequest.Builder(context).data(url).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

/** 封面卡片：上图 + 标题 + 说明，宽屏 4 列 / 窄屏 2 列时等高。 */
@Composable
fun MediaCard(
    title: String,
    supporting: String,
    coverUrl: String?,
    type: MediaType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 340.dp,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLow)
            .clickable(onClick = onClick),
    ) {
        Cover(url = coverUrl, type = type, modifier = Modifier.fillMaxWidth().weight(1f))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 列表行：左侧图标/封面 + 标题 + 说明 + 尾部。 */
@Composable
fun MediaRow(
    title: String,
    supporting: String?,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    coverUrl: String? = null,
    type: MediaType? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            coverUrl != null || type != null -> {
                Cover(
                    url = coverUrl,
                    type = type,
                    modifier = Modifier.size(width = 40.dp, height = 56.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(14.dp))
            }
            icon != null -> {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!supporting.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing() else if (onClick != null) {
            Icon(MiyoruIcons.Chevron, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** 继续阅读横向卡片：封面 + 底部压暗 + 标题/进度。 */
@Composable
fun ResumeCard(
    title: String,
    supporting: String,
    coverUrl: String?,
    type: MediaType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 230.dp,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .width(if (height >= 220.dp) 240.dp else 200.dp)
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Cover(url = coverUrl, type = type, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(type.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(type.label(), fontSize = 11.sp, color = Color.White)
        }
    }
}

/** 网格：按列数把条目切成等宽行，配合外层滚动容器使用。 */
@Composable
fun <T> MediaGrid(
    items: List<T>,
    columns: Int,
    gap: Dp = 16.dp,
    cardHeight: Dp = 340.dp,
    itemContent: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { item ->
                    Box(Modifier.weight(1f)) { itemContent(item) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 数字卡片（统计页）：无封面，只放主数字与说明。 */
@Composable
fun StatCard(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLow)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Column {
            Text(value, fontSize = 22.sp, lineHeight = 28.sp, color = colors.onSurface, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}
