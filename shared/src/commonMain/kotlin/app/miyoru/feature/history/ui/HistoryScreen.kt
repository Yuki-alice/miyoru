package app.miyoru.feature.history.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaKey
import app.miyoru.core.ui.components.CoverImage
import app.miyoru.feature.history.presentation.HistoryViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HistoryScreen(
    onOpenDetail: (MediaKey) -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("历史", style = MaterialTheme.typography.headlineSmall)
        when {
            state.isLoading -> Text("加载中…")
            state.errorMessage != null -> Text("加载失败：${state.errorMessage}")
            state.items.isEmpty() -> Text("还没有阅读或观看记录。")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                items(state.items, key = { it.key.sourceId.value + it.key.mediaType + it.key.remoteId.value }) { item ->
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = { Text(item.author ?: item.key.mediaType.name) },
                        leadingContent = { CoverImage(item.coverUrl, item.title) },
                        trailingContent = {
                            TextButton(onClick = { viewModel.remove(item.key) }) { Text("删除") }
                        },
                        modifier = Modifier.clickable { onOpenDetail(item.key) },
                    )
                }
            }
        }
    }
}
