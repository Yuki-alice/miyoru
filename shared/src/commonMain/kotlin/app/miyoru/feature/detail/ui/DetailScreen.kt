package app.miyoru.feature.detail.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.RemoteId
import app.miyoru.core.ui.components.CoverImage
import app.miyoru.feature.detail.presentation.DetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DetailScreen(
    key: MediaKey,
    onOpenReader: (unitId: RemoteId?) -> Unit,
    viewModel: DetailViewModel = koinViewModel(parameters = { parametersOf(key) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.isLoading -> Text("加载中…")
            state.errorMessage != null -> {
                Text("加载失败：${state.errorMessage}")
                TextButton(onClick = { viewModel.retry() }) { Text("重试") }
            }
            state.detail != null -> {
                val detail = state.detail!!
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoverImage(
                        detail.summary.coverUrl, detail.summary.title,
                        modifier = Modifier, width = 120.dp, height = 160.dp,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(detail.summary.title, style = MaterialTheme.typography.headlineSmall)
                        detail.summary.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenReader(null) }) { Text("开始阅读") }
                    OutlinedButton(onClick = { viewModel.toggleFavorite() }) {
                        Text(if (state.isFavorite) "已收藏" else "加入书架")
                    }
                }
                Text("目录（${state.units.size}）", style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                    items(state.units, key = { it.unitId }) { unit ->
                        ListItem(
                            headlineContent = { Text(unit.title) },
                            modifier = Modifier.clickable { onOpenReader(RemoteId(unit.unitId)) },
                        )
                    }
                }
            }
        }
    }
}
