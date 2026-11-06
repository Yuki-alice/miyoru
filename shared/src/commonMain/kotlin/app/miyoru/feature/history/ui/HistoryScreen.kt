package app.miyoru.feature.history.ui

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
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.ui.components.EmptyState
import app.miyoru.core.ui.components.MediaRow
import app.miyoru.core.ui.components.MiyoruTabs
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.components.icon
import app.miyoru.core.ui.components.label
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.history.presentation.HistoryViewModel
import org.koin.compose.viewmodel.koinViewModel

private val HISTORY_TABS = listOf("全部", "漫画", "小说", "视频")
private val HISTORY_TYPES = listOf(null, MediaType.COMIC, MediaType.NOVEL, MediaType.VIDEO)

/**
 * 历史：类型 Tab + 按“今天/更早”分组的记录列表，可逐条删除或一键清空。
 * 分组按当前顺序切分；精确时间分组待仓储返回时间戳后替换。
 */
@Composable
fun HistoryScreen(
    wide: Boolean,
    onOpenDetail: (MediaKey) -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    var tab by remember { mutableIntStateOf(0) }
    val filtered = state.items.filter { item ->
        val type = HISTORY_TYPES[tab]
        type == null || item.key.mediaType == type
    }
    val recent = filtered.take(3)
    val earlier = filtered.drop(3)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(
                title = "历史",
                wide = wide,
                actions = {
                    IconButton(
                        onClick = { filtered.forEach { viewModel.remove(it.key) } },
                        enabled = filtered.isNotEmpty(),
                    ) {
                        Icon(
                            MiyoruIcons.DeleteSweep,
                            contentDescription = "清空历史",
                            modifier = Modifier.size(22.dp),
                            tint = if (filtered.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { MiyoruTabs(HISTORY_TABS, tab, { tab = it }) }
                when {
                    state.isLoading -> item { Text("加载中…", style = MaterialTheme.typography.bodyMedium) }
                    state.errorMessage != null -> item {
                        Text(
                            "加载失败：${state.errorMessage}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    filtered.isEmpty() -> item {
                        EmptyState(
                            icon = MiyoruIcons.History,
                            title = "还没有浏览记录",
                            hint = "打开任意作品阅读或播放后，记录会按时间出现在这里。",
                        )
                    }
                    else -> {
                        if (recent.isNotEmpty()) {
                            item { SectionTitle("今天", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                            items(recent.size) { index ->
                                val item = recent[index]
                                MediaRow(
                                    title = item.title,
                                    supporting = "${item.key.mediaType.label()} · 继续${if (item.key.mediaType == MediaType.VIDEO) "播放" else "阅读"}",
                                    onClick = { onOpenDetail(item.key) },
                                    icon = item.key.mediaType.icon(),
                                )
                            }
                        }
                        if (earlier.isNotEmpty()) {
                            item { SectionTitle("更早", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                            items(earlier.size) { index ->
                                val item = earlier[index]
                                MediaRow(
                                    title = item.title,
                                    supporting = "${item.key.mediaType.label()} · ${if (item.key.mediaType == MediaType.VIDEO) "已看" else "已读"}",
                                    onClick = { onOpenDetail(item.key) },
                                    icon = item.key.mediaType.icon(),
                                )
                            }
                        }
                    }
                }
                item { PageFooter() }
            }
        }
    }
}
