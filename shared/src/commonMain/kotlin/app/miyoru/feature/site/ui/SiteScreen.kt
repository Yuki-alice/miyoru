package app.miyoru.feature.site.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaKey
import app.miyoru.core.ui.components.CoverImage
import app.miyoru.core.model.SourceId
import app.miyoru.feature.site.presentation.SiteViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SiteScreen(
    sourceId: SourceId,
    onOpenDetail: (MediaKey) -> Unit,
    viewModel: SiteViewModel = koinViewModel(parameters = { parametersOf(sourceId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("站点浏览 · ${sourceId.value}", style = MaterialTheme.typography.headlineSmall)
        TextField(
            value = state.query,
            onValueChange = { viewModel.updateQuery(it) },
            label = { Text("搜索") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { viewModel.submit() }, enabled = !state.isSearching) {
            Text(if (state.isSearching) "搜索中…" else "搜索")
        }
        state.errorMessage?.let { Text("失败：$it") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            items(state.items, key = { it.key.remoteId.value }) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = { Text(item.author ?: "") },
                    modifier = Modifier.clickable { onOpenDetail(item.key) },
                )
            }
        }
    }
}
