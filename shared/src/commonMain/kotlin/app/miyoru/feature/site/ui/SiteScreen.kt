package app.miyoru.feature.site.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import app.miyoru.core.model.SourceId
import app.miyoru.core.ui.components.EmptyState
import app.miyoru.core.ui.components.MediaCard
import app.miyoru.core.ui.components.MediaGrid
import app.miyoru.core.ui.components.MiyoruChips
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.label
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.site.presentation.SiteViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val SITE_FILTERS = listOf("全部", "漫画", "小说", "视频", "最新", "热门")
private val SITE_TYPES = listOf(MediaType.COMIC, MediaType.COMIC, MediaType.NOVEL, MediaType.VIDEO, MediaType.COMIC, MediaType.COMIC)

/**
 * 站点浏览：站内搜索 + 类型/排序筛选 + 封面网格（原型 site 屏）。
 */
@Composable
fun SiteScreen(
    wide: Boolean,
    sourceId: SourceId,
    onBack: () -> Unit,
    onOpenDetail: (MediaKey) -> Unit,
    viewModel: SiteViewModel = koinViewModel(parameters = { parametersOf(sourceId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    val colors = MaterialTheme.colorScheme
    var filter by remember { mutableIntStateOf(0) }

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(
                title = sourceId.value,
                wide = wide,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.submit(SITE_TYPES[filter]) }) {
                        Icon(MiyoruIcons.Search, contentDescription = "搜索", modifier = Modifier.size(22.dp))
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
                        value = state.query,
                        onValueChange = { viewModel.updateQuery(it) },
                        placeholder = { Text("搜索站内作品", style = MaterialTheme.typography.bodyLarge) },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        leadingIcon = { Icon(MiyoruIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            androidx.compose.material3.TextButton(
                                onClick = { viewModel.submit(SITE_TYPES[filter]) },
                                enabled = !state.isSearching,
                            ) { Text(if (state.isSearching) "搜索中…" else "搜索") }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = colors.surfaceContainerHigh,
                            unfocusedContainerColor = colors.surfaceContainerHigh,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { MiyoruChips(SITE_FILTERS, filter, { filter = it }) }
                when {
                    state.errorMessage != null -> item {
                        EmptyState(
                            icon = MiyoruIcons.Search,
                            title = "搜索失败",
                            hint = state.errorMessage ?: "源暂时不可用。",
                        )
                    }
                    state.items.isEmpty() -> item {
                        EmptyState(
                            icon = MiyoruIcons.Source,
                            title = "这里还没有内容",
                            hint = "输入关键词搜索该站点的作品，结果会以封面网格展示。",
                        )
                    }
                    else -> item {
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
                }
                item { PageFooter() }
            }
        }
    }
}
