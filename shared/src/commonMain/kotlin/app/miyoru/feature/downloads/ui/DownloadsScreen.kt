package app.miyoru.feature.downloads.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import app.miyoru.core.ui.components.TextAction
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.downloads.presentation.DownloadsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * 下载：已完成 / 下载中两个分页，行内按状态提供暂停、继续、删除（原型 downloads 屏）。
 */
@Composable
fun DownloadsScreen(
    wide: Boolean,
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    var tab by remember { mutableIntStateOf(0) }
    val running = state.tasks.filter { it.state != "DONE" && it.state != "COMPLETED" }
    val done = state.tasks.filter { it.state == "DONE" || it.state == "COMPLETED" }
    val shown = if (tab == 0) done else running

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(title = "下载", wide = wide, onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { MiyoruTabs(listOf("已完成", "下载中"), tab, { tab = it }) }
                when {
                    state.isLoading -> item { Text("加载中…", style = MaterialTheme.typography.bodyMedium) }
                    state.errorMessage != null -> item {
                        Text(
                            "加载失败：${state.errorMessage}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    shown.isEmpty() -> item {
                        EmptyState(
                            icon = MiyoruIcons.Download,
                            title = if (tab == 0) "还没有已完成的下载" else "没有下载中的任务",
                            hint = "在作品详情或阅读器里选择“下载”，任务会出现在这里，可暂停与续传。",
                        )
                    }
                    else -> items(shown.size) { index ->
                        val task = shown[index]
                        MediaRow(
                            title = task.id,
                            supporting = stateLabel(task.state),
                            icon = if (tab == 0) MiyoruIcons.DownloadDone else MiyoruIcons.Download,
                            onClick = {
                                if (task.state == "QUEUED" || task.state == "PAUSED") viewModel.start(task.id)
                                else viewModel.pause(task.id)
                            },
                            trailing = {
                                Row {
                                    TextAction(
                                        if (task.state == "QUEUED" || task.state == "PAUSED") "继续" else "暂停",
                                    ) {
                                        if (task.state == "QUEUED" || task.state == "PAUSED") viewModel.start(task.id)
                                        else viewModel.pause(task.id)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    TextAction("删除") { viewModel.remove(task.id, true) }
                                }
                            },
                        )
                    }
                }
                item { SectionTitle("批量操作", size = 18, modifier = Modifier.padding(top = 12.dp)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MiyoruButton(
                            label = "全部暂停",
                            onClick = { running.forEach { viewModel.pause(it.id) } },
                            icon = MiyoruIcons.Pause,
                            variant = MiyoruButtonVariant.Tonal,
                        )
                        MiyoruButton(
                            label = "清理已完成",
                            onClick = { done.forEach { viewModel.remove(it.id, false) } },
                            icon = MiyoruIcons.Clean,
                            variant = MiyoruButtonVariant.Outlined,
                        )
                    }
                }
                item { PageFooter() }
            }
        }
    }
}

private fun stateLabel(state: String): String = when (state) {
    "DONE", "COMPLETED" -> "已完成 · 可离线访问"
    "RUNNING" -> "下载中"
    "PAUSED" -> "已暂停 · 可继续"
    "QUEUED" -> "等待中"
    "FAILED" -> "失败 · 可重试"
    else -> state
}
