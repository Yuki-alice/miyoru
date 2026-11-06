package app.miyoru.feature.library.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.ui.components.EmptyState
import app.miyoru.core.ui.components.MediaCard
import app.miyoru.core.ui.components.MediaGrid
import app.miyoru.core.ui.components.MediaRow
import app.miyoru.core.ui.components.MiyoruChips
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.components.icon
import app.miyoru.core.ui.components.label
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.library.presentation.LibraryViewModel
import org.koin.compose.viewmodel.koinViewModel

private val LIBRARY_FILTERS = listOf("全部", "漫画", "小说", "视频")
private val LIBRARY_TYPES = listOf(null, MediaType.COMIC, MediaType.NOVEL, MediaType.VIDEO)

/**
 * 书架：类型筛选 + 封面网格；宽屏补一段“最近阅读”列表（原型 library 屏）。
 */
@Composable
fun LibraryScreen(
    wide: Boolean,
    onOpenDetail: (MediaKey) -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    val selected = LIBRARY_TYPES.indexOf(state.selectedType).coerceAtLeast(0)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(
                title = "书架",
                wide = wide,
                actions = {
                    IconButton(onClick = {}) {
                        Icon(MiyoruIcons.Search, contentDescription = "搜索书架", modifier = Modifier.size(22.dp))
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
            ) {
                item {
                    MiyoruChips(
                        labels = LIBRARY_FILTERS,
                        selected = selected,
                        onSelect = { viewModel.selectType(LIBRARY_TYPES[it]) },
                    )
                }
                when {
                    state.isLoading -> item {
                        Text("加载中…", style = MaterialTheme.typography.bodyMedium)
                    }
                    state.errorMessage != null -> item {
                        Text(
                            "加载失败：${state.errorMessage}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    state.items.isEmpty() -> item {
                        EmptyState(
                            icon = MiyoruIcons.Library,
                            title = "书架是空的",
                            hint = "在详情页点“加入书架”，收藏的漫画、小说和视频都会出现在这里。",
                        )
                    }
                    else -> {
                        item {
                            MediaGrid(
                                items = state.items,
                                columns = if (wide) 4 else 2,
                                cardHeight = if (wide) Dimens.CardHeightWide else Dimens.CardHeightCompact,
                            ) { item ->
                                MediaCard(
                                    title = item.title,
                                    supporting = buildString {
                                        append(item.key.mediaType.label())
                                        if (!item.author.isNullOrBlank()) append(" · ${item.author}")
                                    },
                                    coverUrl = item.coverUrl,
                                    type = item.key.mediaType,
                                    onClick = { onOpenDetail(item.key) },
                                    height = if (wide) Dimens.CardHeightWide else Dimens.CardHeightCompact,
                                )
                            }
                        }
                        item {
                            SectionTitle(
                                if (wide) "最近阅读" else "共 ${state.items.size} 部作品",
                                size = if (wide) 20 else 15,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (wide) {
                            items(state.items.take(2).size) { index ->
                                val item = state.items[index]
                                MediaRow(
                                    title = item.title,
                                    supporting = "${item.key.mediaType.label()} · 继续阅读",
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
