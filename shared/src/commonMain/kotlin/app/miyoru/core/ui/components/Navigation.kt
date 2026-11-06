package app.miyoru.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens

/** 导航项：图标 + 文字，供侧栏与底栏共用。 */
data class NavItem(val label: String, val icon: ImageVector)

/**
 * 页面顶栏。标题与内容左边缘对齐（宽屏 32 / 窄屏 16），返回键沿用原型的位置。
 * 各屏自带顶栏，标题即页面语义（作品详情、第 12 话 · 长夜余火 等）。
 */
@Composable
fun MiyoruTopBar(
    title: String,
    wide: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    Row(
        modifier = modifier.fillMaxWidth().height(Dimens.TopBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(if (onBack == null) pad else pad - 12.dp))
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(MiyoruIcons.Back, contentDescription = "返回", modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        actions()
        Spacer(Modifier.width(pad))
    }
}

/**
 * 宽屏侧边导航栏（80dp）。仅 5 个主 Tab 屏显示，子页沉浸不显示。
 */
@Composable
fun MiyoruNavRail(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(Dimens.RailWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items.forEachIndexed { index, item ->
            RailItem(
                item = item,
                selected = index == selected,
                onClick = { if (index != selected) onSelect(index) },
            )
        }
    }
}

@Composable
private fun RailItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(22.dp),
                tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 窄屏底部导航栏。 */
@Composable
fun MiyoruBottomBar(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { if (!isSelected) onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (isSelected) colors.secondaryContainer else colors.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** 页面骨架：顶栏 + 可滚动内容区，统一宽屏/窄屏边距。 */
@Composable
fun PageColumn(
    title: String,
    wide: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        MiyoruTopBar(title = title, wide = wide, onBack = onBack, actions = actions)
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact),
            content = content,
        )
    }
}

/** 空状态：图标 + 主文案 + 提示 + 可选操作。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** 页脚留白，避免内容贴底。 */
@Composable
fun PageFooter(height: Dp = 32.dp) {
    Spacer(Modifier.height(height))
}
