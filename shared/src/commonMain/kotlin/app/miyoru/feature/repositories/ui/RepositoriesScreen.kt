package app.miyoru.feature.repositories.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.ui.components.EmptyState
import app.miyoru.core.ui.components.MediaRow
import app.miyoru.core.ui.components.MiyoruButton
import app.miyoru.core.ui.components.MiyoruButtonVariant
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.components.SettingRow
import app.miyoru.core.ui.components.TextAction
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.repositories.presentation.RepositoriesViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * 插件仓库：对齐 Mihon / Komikku / Miru 的「扩展仓库」页。
 * 添加时先识别索引并展示身份指纹与风险，用户确认信任后才落库；删除时提示影响范围。
 */
@Composable
fun RepositoriesScreen(
    wide: Boolean,
    onBack: () -> Unit,
    onOpenExtensions: () -> Unit,
    viewModel: RepositoriesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    val colors = MaterialTheme.colorScheme

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(
                title = "插件仓库",
                wide = wide,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(MiyoruIcons.Update, contentDescription = "刷新全部仓库", modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { viewModel.showAddDialog() }) {
                        Icon(MiyoruIcons.Add, contentDescription = "添加仓库", modifier = Modifier.size(22.dp))
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
            ) {
                state.message?.let { message ->
                    item {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.isError) colors.error else colors.primary,
                        )
                    }
                }
                item { SectionTitle("已添加的仓库", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                when {
                    state.isLoading -> item { Text("加载中…", style = MaterialTheme.typography.bodyMedium) }
                    state.repositories.isEmpty() -> item {
                        EmptyState(
                            icon = MiyoruIcons.Source,
                            title = "还没有插件仓库",
                            hint = "应用不内置任何仓库。添加一份你信任的仓库索引链接，系统识别后即可按需安装插件。",
                            action = {
                                MiyoruButton(
                                    label = "添加仓库",
                                    onClick = { viewModel.showAddDialog() },
                                    icon = MiyoruIcons.Add,
                                    variant = MiyoruButtonVariant.Tonal,
                                )
                            },
                        )
                    }
                    else -> state.repositories.forEach { repo ->
                        item {
                            MediaRow(
                                title = repo.name,
                                supporting = buildString {
                                    append("${repo.pluginCount} 个插件")
                                    append(" · 身份指纹 ${repo.fingerprint.take(8)}")
                                    repo.lastError?.let { append(" · $it") }
                                },
                                icon = MiyoruIcons.Source,
                            )
                        }
                        item {
                            SettingRow(
                                label = "启用此仓库",
                                supporting = repo.url,
                                icon = MiyoruIcons.Plugin,
                                checked = repo.enabled,
                                onCheckedChange = { viewModel.setEnabled(repo, it) },
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextAction("立即刷新") { viewModel.refresh(repo.id) }
                                TextAction("移除仓库") { viewModel.requestDelete(repo) }
                                Spacer(Modifier.weight(1f))
                                TextAction("浏览插件") { onOpenExtensions() }
                            }
                        }
                    }
                }
                item { SectionTitle("安全提示", size = 18, modifier = Modifier.padding(top = 12.dp)) }
                item {
                    MediaRow(
                        title = "仓库与插件都是第三方代码",
                        supporting = "仓库 ID、链接或发布者公钥变化时需重新确认；插件只能从来源仓库更新",
                        icon = MiyoruIcons.Info,
                    )
                }
                item { PageFooter() }
            }
        }
    }

    if (state.showAddDialog) {
        AddRepositoryDialog(state, viewModel)
    }
    state.pendingDelete?.let { repo ->
        DeleteRepositoryDialog(repo = repo, count = state.deletePluginCount, viewModel = viewModel)
    }
}

@Composable
private fun AddRepositoryDialog(
    state: app.miyoru.feature.repositories.presentation.RepositoriesUiState,
    viewModel: RepositoriesViewModel,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { viewModel.dismissAddDialog() },
        title = { Text("添加插件仓库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "粘贴仓库索引链接（HTTPS JSON 文件）。应用不内置、不推荐任何仓库。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextField(
                    value = state.addUrl,
                    onValueChange = { viewModel.updateUrl(it) },
                    placeholder = { Text("https://…/index.json") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceContainerHigh,
                        unfocusedContainerColor = colors.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.isInspecting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                state.dialogError?.let {
                    Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall)
                }
                state.preview?.let { preview ->
                    Text("已识别：${preview.name}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "插件 ${preview.pluginCount} 个 · 格式 v${preview.schemaVersion} · " +
                            (if (preview.hasSignature) "带发布者签名" else "未签名"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "身份指纹 ${preview.fingerprint.take(16)}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    if (preview.alreadyAdded) {
                        Text(
                            "索引内容已变化：确认后将重新信任此仓库。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                        )
                    }
                    Text(
                        "第三方插件可执行代码，请只添加你信任的仓库。确认后仍需在插件商店逐个安装。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (state.preview != null) {
                TextButton(onClick = { viewModel.confirmAdd() }) { Text("信任并添加") }
            } else {
                TextButton(onClick = { viewModel.inspect() }, enabled = !state.isInspecting) {
                    Text("识别仓库")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissAddDialog() }) { Text("取消") }
        },
    )
}

@Composable
private fun DeleteRepositoryDialog(
    repo: app.miyoru.domain.plugin.model.PluginRepo,
    count: Int,
    viewModel: RepositoriesViewModel,
) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissDelete() },
        title = { Text("移除仓库「${repo.name}」") },
        text = {
            Text(
                if (count > 0) {
                    "该仓库下有 $count 个已安装插件。仅移除仓库会保留插件，但它们将无法检查更新；" +
                        "同时卸载会删除插件文件。两种操作都不会删除收藏、历史与阅读进度。"
                } else {
                    "该仓库下没有已安装插件。移除后其插件目录将不再显示。"
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { viewModel.confirmDelete(false) }) { Text("仅移除仓库") }
        },
        dismissButton = {
            Row {
                if (count > 0) {
                    TextButton(onClick = { viewModel.confirmDelete(true) }) { Text("同时卸载插件") }
                }
                TextButton(onClick = { viewModel.dismissDelete() }) { Text("取消") }
            }
        },
    )
}
