package app.miyoru.feature.sources.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.feature.sources.presentation.SourcesViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SourcesScreen(viewModel: SourcesViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("数据源管理", style = MaterialTheme.typography.headlineSmall)
        when {
            state.isLoading -> Text("加载中…")
            state.errorMessage != null -> Text("加载失败：${state.errorMessage}")
            state.plugins.isEmpty() -> Text("没有已安装的插件。")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.plugins, key = { it.id }) { plugin ->
                    ListItem(
                        headlineContent = { Text("${plugin.id} ${plugin.version}") },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = plugin.enabled,
                                    onCheckedChange = { viewModel.setEnabled(plugin.id, it) },
                                )
                                TextButton(onClick = { viewModel.uninstall(plugin.id) }) { Text("删除") }
                            }
                        },
                    )
                }
            }
        }
    }
}
