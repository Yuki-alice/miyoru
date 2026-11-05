package app.miyoru.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaKey
import app.miyoru.feature.home.presentation.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onOpenDetail: (MediaKey) -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("首页", style = MaterialTheme.typography.headlineSmall)
        Text("书架 ${state.favoritesCount} 本收藏", style = MaterialTheme.typography.bodyMedium)
        when {
            state.isLoading -> Text("加载中…")
            state.errorMessage != null -> Text("加载失败：${state.errorMessage}")
            state.continueReading.isEmpty() -> Text("还没有阅读记录，去书架或浏览找点内容吧。")
            else -> {
                Text("继续阅读", style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.continueReading, key = { it.key.sourceId.value + it.key.remoteId.value }) { item ->
                        ListItem(
                            headlineContent = { Text(item.title) },
                            supportingContent = { Text(item.author ?: item.key.mediaType.name) },
                            modifier = Modifier.clickable { onOpenDetail(item.key) },
                        )
                    }
                }
            }
        }
    }
}
