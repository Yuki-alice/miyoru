package app.miyoru.feature.browse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.SourceId
import app.miyoru.core.ui.components.EmptyState
import app.miyoru.core.ui.components.MediaRow
import app.miyoru.core.ui.components.MiyoruButton
import app.miyoru.core.ui.components.MiyoruButtonVariant
import app.miyoru.core.ui.components.MiyoruSearchBar
import app.miyoru.core.ui.components.MiyoruTabs
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.components.label
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.browse.presentation.BrowseViewModel
import org.koin.compose.viewmodel.koinViewModel

private val BROWSE_TABS = listOf("站点", "数据源", "插件订阅")

/**
 * 浏览：站点 / 数据源 / 插件订阅三个分面，数据源来自已安装插件。
 * 结构对照原型 browse 屏：搜索 + Tab + 已订阅数据源 + 插件入口。
 */
@Composable
fun BrowseScreen(
    wide: Boolean,
    onOpenSources: () -> Unit,
    onOpenSite: (SourceId) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenRepositories: () -> Unit,
    viewModel: BrowseViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    var tab by remember { mutableIntStateOf(0) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(
                title = "浏览",
                wide = wide,
                actions = {
                    IconButton(onClick = onOpenSources) {
                        Icon(MiyoruIcons.Search, contentDescription = "搜索站点", modifier = Modifier.size(22.dp))
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
            ) {
                item { MiyoruTabs(BROWSE_TABS, tab, { tab = it }) }
                item { MiyoruSearchBar("搜索站点、数据源与插件") }

                item { SectionTitle("已订阅数据源", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                when {
                    state.isLoading -> item { Text("加载中…", style = MaterialTheme.typography.bodyMedium) }
                    state.errorMessage != null -> item {
                        Text(
                            "加载失败：${state.errorMessage}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    state.sources.isEmpty() -> item {
                        EmptyState(
                            icon = MiyoruIcons.Source,
                            title = "还没有订阅数据源",
                            hint = "安装源插件后，插件提供的数据源会出现在这里。",
                            action = {
                                MiyoruButton(
                                    label = "前往插件商店",
                                    onClick = onOpenExtensions,
                                    icon = MiyoruIcons.Plugin,
                                    variant = MiyoruButtonVariant.Tonal,
                                )
                            },
                        )
                    }
                    else -> items(state.sources.size) { index ->
                        val source = state.sources[index]
                        MediaRow(
                            title = source.name,
                            supporting = buildString {
                                append(source.mediaTypes.joinToString("/") { it.label() }.ifBlank { "内容源" })
                                append(" · ")
                                append(if (source.enabled) "已订阅" else "已停用")
                            },
                            icon = MiyoruIcons.Source,
                            onClick = { onOpenSite(source.id) },
                        )
                    }
                }

                item { SectionTitle("插件订阅", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    MediaRow(
                        title = "插件仓库",
                        supporting = "添加仓库链接、识别并确认信任、刷新索引",
                        icon = MiyoruIcons.Source,
                        onClick = onOpenRepositories,
                    )
                }
                item {
                    MediaRow(
                        title = "站点插件",
                        supporting = "${state.sources.size} 个数据源可用",
                        icon = MiyoruIcons.Plugin,
                        onClick = onOpenExtensions,
                    )
                }
                item {
                    MediaRow(
                        title = "管理数据源与插件",
                        supporting = "启用、停用、检查更新",
                        icon = MiyoruIcons.Tune,
                        onClick = onOpenSources,
                    )
                }
                item { PageFooter() }
            }
        }
    }
}
