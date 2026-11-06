package app.miyoru.feature.more.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.miyoru.core.ui.components.MediaRow
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.navigation.About
import app.miyoru.navigation.Categories
import app.miyoru.navigation.Downloads
import app.miyoru.navigation.Extensions
import app.miyoru.navigation.Import
import app.miyoru.navigation.Repositories
import app.miyoru.navigation.Settings
import app.miyoru.navigation.Statistics
import app.miyoru.navigation.Storage

/**
 * 更多：内容管理与扩展系统两组的入口页，是 7 个子页的路由汇总（原型 more 屏）。
 */
@Composable
fun MoreScreen(wide: Boolean, onNavigate: (Any) -> Unit) {
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(title = "更多", wide = wide)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { SectionTitle("内容管理", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    MediaRow(
                        title = "下载",
                        supporting = "离线内容与下载队列",
                        icon = MiyoruIcons.Download,
                        onClick = { onNavigate(Downloads) },
                    )
                }
                item {
                    MediaRow(
                        title = "分类",
                        supporting = "按自定义分类整理书架",
                        icon = MiyoruIcons.Category,
                        onClick = { onNavigate(Categories) },
                    )
                }
                item {
                    MediaRow(
                        title = "统计",
                        supporting = "本月阅读与观看时长",
                        icon = MiyoruIcons.Statistics,
                        onClick = { onNavigate(Statistics) },
                    )
                }

                item { SectionTitle("扩展与系统", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    MediaRow(
                        title = "插件仓库",
                        supporting = "添加仓库链接、识别并确认信任",
                        icon = MiyoruIcons.Source,
                        onClick = { onNavigate(Repositories) },
                    )
                }
                item {
                    MediaRow(
                        title = "插件商店",
                        supporting = "按仓库浏览插件、按需安装与更新",
                        icon = MiyoruIcons.Plugin,
                        onClick = { onNavigate(Extensions) },
                    )
                }
                item {
                    MediaRow(
                        title = "本地导入",
                        supporting = "TXT / EPUB / CBZ 文件",
                        icon = MiyoruIcons.Import,
                        onClick = { onNavigate(Import) },
                    )
                }
                item {
                    MediaRow(
                        title = "设置",
                        supporting = "外观、阅读、播放与通知",
                        icon = MiyoruIcons.Settings,
                        onClick = { onNavigate(Settings) },
                    )
                }
                item {
                    MediaRow(
                        title = "数据与存储",
                        supporting = "缓存、下载与备份",
                        icon = MiyoruIcons.Storage,
                        onClick = { onNavigate(Storage) },
                    )
                }
                item {
                    MediaRow(
                        title = "关于",
                        supporting = "版本 0.1.0 · MIT",
                        icon = MiyoruIcons.Info,
                        onClick = { onNavigate(About) },
                    )
                }
                item { PageFooter() }
            }
        }
    }
}
