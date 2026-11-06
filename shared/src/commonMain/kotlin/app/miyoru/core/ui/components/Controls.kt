package app.miyoru.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens

/** 顶部搜索框（原型 56dp、28dp 圆角、surfaceContainerHigh）。 */
@Composable
fun MiyoruSearchBar(
    placeholder: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.SearchHeight)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainerHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MiyoruIcons.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一级 Tab（原型 48dp 高、选中主色 + 底部指示条）。 */
@Composable
fun MiyoruTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().height(Dimens.TabHeight)) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { if (!active) onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (active) colors.primary else colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (active) 0.6f else 0f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (active) colors.primary else colors.surface),
                )
            }
        }
    }
}

/** 筛选 chip 组；超过 8 个自动换行（窄屏 4 个一行）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MiyoruChips(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) colors.secondaryContainer else colors.surfaceContainerHigh)
                    .clickable { if (!active) onSelect(index) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant,
                )
            }
        }
    }
}

enum class MiyoruButtonVariant { Filled, Tonal, Outlined }

@Composable
fun MiyoruButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: MiyoruButtonVariant = MiyoruButtonVariant.Filled,
) {
    val colors = MaterialTheme.colorScheme
    val bg = when (variant) {
        MiyoruButtonVariant.Filled -> colors.primary
        MiyoruButtonVariant.Tonal -> colors.secondaryContainer
        MiyoruButtonVariant.Outlined -> colors.surface
    }
    val fg = when (variant) {
        MiyoruButtonVariant.Filled -> colors.onPrimary
        MiyoruButtonVariant.Tonal -> colors.onSecondaryContainer
        MiyoruButtonVariant.Outlined -> colors.primary
    }
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .then(
                if (variant == MiyoruButtonVariant.Outlined) {
                    Modifier.border(1.dp, colors.outlineVariant, RoundedCornerShape(24.dp))
                } else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** 细进度条（圆角、无动画抖动），用于阅读/播放/存储占用。 */
@Composable
fun MiyoruProgress(
    value: Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(colors.primary),
        )
    }
}

/** 设置类行：图标 + 标题 + 说明 + 尾部（开关/箭头/文字）。 */
@Composable
fun SettingRow(
    label: String,
    supporting: String?,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    trailingText: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.RowHeight)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            checked != null && onCheckedChange != null -> Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
            )
            trailingText != null -> Text(trailingText, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            else -> if (onClick != null) {
                Icon(MiyoruIcons.Chevron, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** 分段小标题（原型 18-22sp）。 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 22,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = size.sp,
            lineHeight = (size * 1.3).sp,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

@Composable
fun TextAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun OutlinedAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(label, style = MaterialTheme.typography.labelLarge) }
}
