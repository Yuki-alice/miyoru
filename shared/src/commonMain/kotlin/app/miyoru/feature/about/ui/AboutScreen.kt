package app.miyoru.feature.about.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.miyoru.core.ui.components.MediaRow
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens

/**
 * 关于：版本、一句话说明与项目链接（原型 about 屏）。
 */
@Composable
fun AboutScreen(
    wide: Boolean,
    onBack: () -> Unit,
    onOpenStorage: () -> Unit,
) {
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    val colors = MaterialTheme.colorScheme

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(title = "关于", wide = wide, onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("miyoru", fontSize = 28.sp, lineHeight = 36.sp, color = colors.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "版本 0.1.0 · MIT 开源",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceContainerLow)
                            .padding(20.dp),
                    ) {
                        Text("一个书架，三种内容", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "漫画、小说与视频共用追更、收藏与进度；支持插件数据源、离线下载与本地导入。" +
                                "应用不内置任何内容源，也不分发受版权保护的内容。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                item { SectionTitle("项目", size = 18, modifier = Modifier.padding(top = 16.dp)) }
                item {
                    MediaRow(
                        title = "开源许可",
                        supporting = "MIT · 第三方组件致谢",
                        icon = MiyoruIcons.Gavel,
                    )
                }
                item {
                    MediaRow(
                        title = "更新日志",
                        supporting = "查看版本变更",
                        icon = MiyoruIcons.Description,
                    )
                }
                item {
                    MediaRow(
                        title = "反馈问题",
                        supporting = "提交问题与建议",
                        icon = MiyoruIcons.Bug,
                    )
                }
                item {
                    MediaRow(
                        title = "数据与存储",
                        supporting = "备份与清理",
                        icon = MiyoruIcons.Storage,
                        onClick = onOpenStorage,
                    )
                }
                item {
                    Text(
                        "内容由用户自行配置的来源提供，应用仅提供接入与管理工具。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                item { PageFooter() }
            }
        }
    }
}
