package app.miyoru.feature.extensions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.ui.components.EmptyState
import app.miyoru.core.ui.components.MediaRow
import app.miyoru.core.ui.components.MiyoruButtonVariant
import app.miyoru.core.ui.components.MiyoruButton
import app.miyoru.core.ui.components.MiyoruTabs
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.components.TextAction
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.domain.plugin.model.CatalogEntry
import app.miyoru.domain.plugin.model.CatalogEntryState
import app.miyoru.feature.extensions.presentation.ExtensionsUiState
import app.miyoru.feature.extensions.presentation.ExtensionsViewModel
import app.miyoru.feature.extensions.presentation.TAB_AVAILABLE
import app.miyoru.feature.extensions.presentation.TAB_INSTALLED
import app.miyoru.feature.extensions.presentation.TAB_UPDATES
import org.koin.compose.viewmodel.koinViewModel

private val EXTENSION_TABS = listOf("可安装", "有更新", "已安装")

/**
 * 插件商店：按仓库分组浏览插件目录并按需安装。
 * 没有「输入 ZIP 地址」入口；添加来源统一在「插件仓库」页完成。
 */
@Composable
fun ExtensionsScreen(
    wide: Boolean,
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenRepositories: () -> Unit,
    viewModel: ExtensionsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    val colors = MaterialTheme.colorScheme
    val groups = state.catalog.groups
    var query by remember { mutableStateOf("") }
    val keyword = query.trim()
    val matches: (CatalogEntry) -> Boolean = { entry ->
        keyword.isEmpty() || entry.name.contains(keyword, ignoreCase = true) ||
            entry.pluginId.contains(keyword, ignoreCase = true)
    }

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(
                title = "插件商店",
                wide = wide,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(MiyoruIcons.Update, contentDescription = "刷新仓库", modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onOpenRepositories) {
                        Icon(MiyoruIcons.Source, contentDescription = "插件仓库", modifier = Modifier.size(22.dp))
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
            ) {
                item {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜索仓库内的插件", style = MaterialTheme.typography.bodyLarge) },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        leadingIcon = {
                            Icon(MiyoruIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = colors.surfaceContainerHigh,
                            unfocusedContainerColor = colors.surfaceContainerHigh,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                item { MiyoruTabs(EXTENSION_TABS, state.tab, { viewModel.selectTab(it) }) }
                state.message?.let { message ->
                    item {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.isError) colors.error else colors.primary,
                        )
                    }
                }

                if (groups.isEmpty()) {
                    item {
                        EmptyState(
                            icon = MiyoruIcons.Source,
                            title = "还没有插件仓库",
                            hint = "先添加一份你信任的仓库索引链接，系统识别后才能在这里按需安装插件。",
                            action = {
                                MiyoruButton(
                                    label = "添加插件仓库",
                                    onClick = onOpenRepositories,
                                    icon = MiyoruIcons.Add,
                                    variant = MiyoruButtonVariant.Tonal,
                                )
                            },
                        )
                    }
                } else {
                    when (state.tab) {
                        TAB_AVAILABLE -> groups.forEach { group ->
                            val entries = group.entries.filter {
                                it.state != CatalogEntryState.INSTALLED &&
                                    it.state != CatalogEntryState.UPDATE_AVAILABLE &&
                                    matches(it)
                            }
                            if (entries.isNotEmpty()) {
                                item { SectionTitle(group.repository.name, size = 18, modifier = Modifier.padding(top = 8.dp)) }
                                entries.forEach { entry ->
                                    item { CatalogEntryRow(entry, state, viewModel, onInstallOnly = true) }
                                }
                            }
                        }
                        TAB_UPDATES -> groups.forEach { group ->
                            val entries = group.entries.filter { it.state == CatalogEntryState.UPDATE_AVAILABLE && matches(it) }
                            if (entries.isNotEmpty()) {
                                item { SectionTitle(group.repository.name, size = 18, modifier = Modifier.padding(top = 8.dp)) }
                                entries.forEach { entry ->
                                    item { CatalogEntryRow(entry, state, viewModel, onInstallOnly = false) }
                                }
                            }
                        }
                        TAB_INSTALLED -> {
                            groups.forEach { group ->
                                val entries = group.entries.filter {
                                    (it.state == CatalogEntryState.INSTALLED ||
                                        it.state == CatalogEntryState.UPDATE_AVAILABLE) &&
                                        matches(it)
                                }
                                if (entries.isNotEmpty()) {
                                    item { SectionTitle(group.repository.name, size = 18, modifier = Modifier.padding(top = 8.dp)) }
                                    entries.forEach { entry ->
                                        item { CatalogEntryRow(entry, state, viewModel, onInstallOnly = false) }
                                    }
                                }
                            }
                            if (state.catalog.orphans.isNotEmpty()) {
                                item { SectionTitle("来源仓库已移除", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                                state.catalog.orphans.forEach { orphan ->
                                    item {
                                        MediaRow(
                                            title = orphan.id,
                                            supporting = "v${orphan.version} · 无法检查更新，需重新从仓库安装",
                                            icon = MiyoruIcons.Plugin,
                                            trailing = {
                                                TextAction("卸载") { viewModel.uninstall(orphan.id) }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { SectionTitle("来源管理", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    MediaRow(
                        title = "管理插件仓库",
                        supporting = "添加仓库、识别信任、刷新索引与移除",
                        icon = MiyoruIcons.Source,
                        onClick = onOpenRepositories,
                    )
                }
                item {
                    MediaRow(
                        title = "数据源管理",
                        supporting = "安装后回到此处启用",
                        icon = MiyoruIcons.Tune,
                        onClick = onOpenSources,
                    )
                }
                item { PageFooter() }
            }
        }
    }

    state.pendingInstall?.let { entry ->
        InstallConfirmDialog(entry, state, viewModel)
    }
}

@Composable
private fun CatalogEntryRow(
    entry: CatalogEntry,
    state: ExtensionsUiState,
    viewModel: ExtensionsViewModel,
    onInstallOnly: Boolean,
) {
    val busy = entry.pluginId in state.busy || "install" in state.busy
    val colors = MaterialTheme.colorScheme
    MediaRow(
        title = entry.name,
        supporting = buildString {
            append("v${entry.version}")
            if (entry.mediaTypes.isNotEmpty()) append(" · ${entry.mediaTypes.joinToString("/")}")
            if (entry.installedVersion != null) append(" · 已装 v${entry.installedVersion}")
            entry.reason?.let { append(" · $it") }
        },
        icon = MiyoruIcons.Plugin,
        trailing = {
            when {
                busy -> Text("处理中…", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                entry.state == CatalogEntryState.AVAILABLE ->
                    TextAction("安装") { viewModel.requestInstall(entry) }
                entry.state == CatalogEntryState.UPDATE_AVAILABLE ->
                    TextAction("更新") { viewModel.update(entry.pluginId) }
                !onInstallOnly && entry.state == CatalogEntryState.INSTALLED ->
                    TextAction("卸载") { viewModel.uninstall(entry.pluginId) }
                entry.state == CatalogEntryState.INCOMPATIBLE ||
                    entry.state == CatalogEntryState.SOURCE_MISMATCH ||
                    entry.state == CatalogEntryState.REPOSITORY_DISABLED ->
                    Text("不可用", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                else -> Unit
            }
        },
    )
}

@Composable
private fun InstallConfirmDialog(
    entry: CatalogEntry,
    state: ExtensionsUiState,
    viewModel: ExtensionsViewModel,
) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissInstall() },
        title = { Text("安装 ${entry.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("版本 ${entry.version} · 来源仓库 ${entry.repositoryId}")
                Text(
                    "媒体类型：${entry.mediaTypes.joinToString("、").ifBlank { "未声明" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "能力：${entry.capabilities.joinToString("、").ifBlank { "未声明" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "申请访问的域名：${entry.domains.joinToString("、").ifBlank { "未声明" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "插件是第三方代码，只能经宿主访问上述域名。安装后需在数据源页启用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.confirmInstall() },
                enabled = "install" !in state.busy,
            ) { Text("确认安装") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissInstall() }) { Text("取消") }
        },
    )
}
