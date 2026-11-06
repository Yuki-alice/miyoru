package app.miyoru.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.miyoru.core.ui.components.MiyoruSearchBar
import app.miyoru.core.ui.components.MiyoruTabs
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.ResumeCard
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.components.icon
import app.miyoru.core.ui.components.label
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.home.presentation.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

private val HOME_TABS = listOf("全部", "漫画", "小说", "视频")

/**
 * 首页：搜索 → 类型切换 → 继续阅读横滑 → 最近更新列表。
 * 结构对照原型 home 屏；数据来自本地历史/收藏，无内容时给空态。
 */
@Composable
fun HomeScreen(
    wide: Boolean,
    onOpenDetail: (MediaKey) -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    var tab by remember { mutableIntStateOf(0) }
    // 原型：宽屏横滑 4 张、窄屏 3 张，最近更新固定 3 条
    val resume = state.continueReading
    val recent = resume.take(if (wide) 4 else 3)
    val updated = resume.take(3)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(
                title = "首页",
                wide = wide,
                actions = {
                    IconButton(onClick = {}) {
                        Icon(MiyoruIcons.Search, contentDescription = "搜索", modifier = Modifier.size(22.dp))
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
            ) {
                item { MiyoruSearchBar("搜索作品、作者或来源") }
                item { MiyoruTabs(HOME_TABS, tab, { tab = it }) }

                item { SectionTitle("继续阅读", modifier = Modifier.padding(top = 8.dp)) }
                item {
                    if (resume.isEmpty()) {
                        EmptyState(
                            icon = MiyoruIcons.Library,
                            title = "还没有阅读记录",
                            hint = "去书架或浏览里找点内容，阅读进度会自动记录在这里。",
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                            items(recent.size) { index ->
                                val item = recent[index]
                                ResumeCard(
                                    title = item.title,
                                    supporting = "${item.key.mediaType.label()} · 继续${if (item.key.mediaType == MediaType.VIDEO) "观看" else "阅读"}",
                                    coverUrl = item.coverUrl,
                                    type = item.key.mediaType,
                                    onClick = { onOpenDetail(item.key) },
                                    height = if (wide) 230.dp else 200.dp,
                                )
                            }
                        }
                    }
                }

                item { SectionTitle("最近更新", modifier = Modifier.padding(top = 8.dp)) }
                if (state.isLoading) {
                    item { Text("加载中…", style = MaterialTheme.typography.bodyMedium) }
                } else if (state.errorMessage != null) {
                    item {
                        Text(
                            "加载失败：${state.errorMessage}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else if (resume.isEmpty()) {
                    item {
                        EmptyState(
                            icon = MiyoruIcons.Update,
                            title = "还没有更新",
                            hint = "订阅数据源并收藏作品后，更新会显示在这里。",
                            action = {
                                TextButton(onClick = onOpenLibrary) { Text("前往书架") }
                            },
                        )
                    }
                } else {
                    items(updated.size) { index ->
                        val item = updated[index]
                        MediaRow(
                            title = item.title,
                            supporting = "${item.key.mediaType.label()} · 有更新",
                            icon = item.key.mediaType.icon(),
                            onClick = { onOpenDetail(item.key) },
                        )
                    }
                }
                item { PageFooter() }
            }
        }
    }
}
