package app.miyoru.feature.categories.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaType
import app.miyoru.core.ui.components.EmptyState
import app.miyoru.core.ui.components.MediaRow
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.components.icon
import app.miyoru.core.ui.components.label
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.categories.presentation.CategoriesViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * 分类：当前按内容类型汇总书架条目，作为分类入口（自定义分类为 P1）。
 */
@Composable
fun CategoriesScreen(
    wide: Boolean,
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: CategoriesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    val total = state.entries.sumOf { it.count }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(
                title = "分类",
                wide = wide,
                onBack = onBack,
                actions = {
                    IconButton(onClick = {}) {
                        Icon(MiyoruIcons.Add, contentDescription = "新建分类", modifier = Modifier.size(22.dp))
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { SectionTitle("按类型", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                if (state.isLoading) {
                    item { androidx.compose.material3.Text("加载中…", style = MaterialTheme.typography.bodyMedium) }
                } else if (total == 0) {
                    item {
                        EmptyState(
                            icon = MiyoruIcons.Category,
                            title = "还没有可分类的作品",
                            hint = "收藏作品后会按漫画、小说、视频自动归类。",
                        )
                    }
                } else {
                    items(state.entries.size) { index ->
                        val entry = state.entries[index]
                        MediaRow(
                            title = entry.type.label(),
                            supporting = "${entry.count} 部作品",
                            icon = entry.type.icon(),
                            onClick = onOpenLibrary,
                        )
                    }
                }
                item { SectionTitle("自定义分类", size = 18, modifier = Modifier.padding(top = 12.dp)) }
                item {
                    MediaRow(
                        title = "尚未创建自定义分类",
                        supporting = "自定义分组与批量整理属于 P1",
                        icon = MiyoruIcons.Add,
                    )
                }
                item { PageFooter() }
            }
        }
    }
}
