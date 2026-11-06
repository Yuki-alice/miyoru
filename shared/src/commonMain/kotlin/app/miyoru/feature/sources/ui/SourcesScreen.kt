package app.miyoru.feature.sources.ui

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
import app.miyoru.core.ui.components.EmptyState
import app.miyoru.core.ui.components.MediaRow
import app.miyoru.core.ui.components.MiyoruButton
import app.miyoru.core.ui.components.MiyoruButtonVariant
import app.miyoru.core.ui.components.MiyoruTabs
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.components.SettingRow
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.sources.presentation.SourcesViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * 数据源：已订阅插件源的开关与卸载，底部提供插件商店入口（原型 sources 屏）。
 */
@Composable
fun SourcesScreen(
    wide: Boolean,
    onBack: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenRepositories: () -> Unit,
    viewModel: SourcesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    var tab by remember { mutableIntStateOf(0) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(
                title = "数据源",
                wide = wide,
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenExtensions) {
                        Icon(MiyoruIcons.Add, contentDescription = "添加数据源", modifier = Modifier.size(22.dp))
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { MiyoruTabs(listOf("已订阅", "全部来源"), tab, { tab = it }) }
                item { SectionTitle("已订阅", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                when {
                    state.isLoading -> item { Text("加载中…", style = MaterialTheme.typography.bodyMedium) }
                    state.errorMessage != null -> item {
                        Text(
                            "加载失败：${state.errorMessage}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    state.plugins.isEmpty() -> item {
                        EmptyState(
                            icon = MiyoruIcons.Plugin,
                            title = "没有已安装的插件",
                            hint = "安装源插件后，插件提供的数据源会出现在这里。",
                        )
                    }
                    else -> items(state.plugins.size) { index ->
                        val plugin = state.plugins[index]
                        SettingRow(
                            label = plugin.id,
                            supporting = "v${plugin.version} · ${if (plugin.enabled) "已启用" else "已停用"}",
                            icon = MiyoruIcons.Plugin,
                            checked = plugin.enabled,
                            onCheckedChange = { viewModel.setEnabled(plugin.id, it) },
                        )
                    }
                }
                item { SectionTitle("可用来源", size = 18, modifier = Modifier.padding(top = 12.dp)) }
                item {
                    MediaRow(
                        title = "插件提供的来源",
                        supporting = "安装插件后可在此启用",
                        icon = MiyoruIcons.Plugin,
                        onClick = onOpenExtensions,
                    )
                }
                item {
                    MediaRow(
                        title = "检查来源更新",
                        supporting = "在插件商店里检查更新",
                        icon = MiyoruIcons.Update,
                        onClick = onOpenExtensions,
                    )
                }
                item {
                    MediaRow(
                        title = "插件仓库",
                        supporting = "添加并管理插件来源仓库",
                        icon = MiyoruIcons.Source,
                        onClick = onOpenRepositories,
                    )
                }
                item {
                    MiyoruButton(
                        label = "添加数据源",
                        onClick = onOpenExtensions,
                        icon = MiyoruIcons.Add,
                        variant = MiyoruButtonVariant.Tonal,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                item { PageFooter() }
            }
        }
    }
}
