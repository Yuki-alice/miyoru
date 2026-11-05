package app.miyoru.feature.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.ui.components.CoverImage
import app.miyoru.feature.library.presentation.LibraryViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryScreen(
    onOpenDetail: (MediaKey) -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("书架", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.selectedType == null,
                onClick = { viewModel.selectType(null) },
                label = { Text("全部") },
            )
            MediaType.entries.forEach { type ->
                FilterChip(
                    selected = state.selectedType == type,
                    onClick = { viewModel.selectType(type) },
                    label = { Text(type.name) },
                )
            }
        }
        when {
            state.isLoading -> Text("加载中…")
            state.errorMessage != null -> Text("加载失败：${state.errorMessage}")
            state.items.isEmpty() -> Text("书架是空的，去首页或浏览添加收藏。")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.items, key = { it.key.sourceId.value + it.key.mediaType + it.key.remoteId.value }) { item ->
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = { Text(item.author ?: item.key.mediaType.name) },
                        leadingContent = { CoverImage(item.coverUrl, item.title) },
                        modifier = Modifier.clickable { onOpenDetail(item.key) },
                    )
                }
            }
        }
    }
}
