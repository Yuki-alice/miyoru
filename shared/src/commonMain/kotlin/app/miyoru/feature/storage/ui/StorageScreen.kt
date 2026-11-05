package app.miyoru.feature.storage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.feature.storage.presentation.StorageViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StorageScreen(viewModel: StorageViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("数据与存储", style = MaterialTheme.typography.headlineSmall)
        if (state.isLoading) {
            Text("加载中…")
        } else {
            Text("缓存占用：${state.cacheBytes / 1024} KB")
            Button(onClick = { viewModel.clearCache() }) { Text("清理缓存") }
            Text("下载文件与收藏数据只能在各自页面显式删除，不会被自动清理。")
        }
    }
}
