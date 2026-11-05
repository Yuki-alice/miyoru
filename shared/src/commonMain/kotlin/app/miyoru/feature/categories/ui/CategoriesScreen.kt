package app.miyoru.feature.categories.ui

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
import app.miyoru.feature.categories.presentation.CategoriesViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CategoriesScreen(
    onOpenLibrary: () -> Unit,
    viewModel: CategoriesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("分类", style = MaterialTheme.typography.headlineSmall)
        Text("按收藏的媒体类型分组，点击进入书架查看。", style = MaterialTheme.typography.bodyMedium)
        when {
            state.isLoading -> Text("加载中…")
            state.entries.isEmpty() -> Text("书架是空的。")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.entries, key = { it.type.name }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.type.name) },
                        supportingContent = { Text("${entry.count} 本收藏") },
                        modifier = Modifier.clickable { onOpenLibrary() },
                    )
                }
            }
        }
    }
}
