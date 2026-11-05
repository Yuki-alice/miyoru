package app.miyoru.feature.downloads.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import app.miyoru.feature.downloads.presentation.DownloadsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("下载", style = MaterialTheme.typography.headlineSmall)
        when {
            state.isLoading -> Text("加载中…")
            state.errorMessage != null -> Text("加载失败：${state.errorMessage}")
            state.tasks.isEmpty() -> Text("没有下载任务。")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.tasks, key = { it.id }) { task ->
                    ListItem(
                        headlineContent = { Text(task.id) },
                        supportingContent = { Text(task.state) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { viewModel.start(task.id) }) { Text("开始") }
                                TextButton(onClick = { viewModel.pause(task.id) }) { Text("暂停") }
                                TextButton(onClick = { viewModel.remove(task.id, deleteFiles = true) }) { Text("删除") }
                            }
                        },
                    )
                }
            }
        }
    }
}
