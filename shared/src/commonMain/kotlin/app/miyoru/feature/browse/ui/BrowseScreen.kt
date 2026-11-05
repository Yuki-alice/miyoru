package app.miyoru.feature.browse.ui

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
import app.miyoru.core.model.SourceId
import app.miyoru.feature.browse.presentation.BrowseViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BrowseScreen(
    onOpenSources: () -> Unit,
    onOpenSite: (SourceId) -> Unit,
    onOpenExtensions: () -> Unit,
    viewModel: BrowseViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("浏览", style = MaterialTheme.typography.headlineSmall)
        when {
            state.isLoading -> Text("加载中…")
            state.errorMessage != null -> Text("加载失败：${state.errorMessage}")
            state.sources.isEmpty() -> {
                Text("还没有订阅数据源。")
                Text("去插件商店安装插件后回到这里。", modifier = Modifier.clickable { onOpenExtensions() })
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.sources, key = { it.id.value }) { source ->
                    ListItem(
                        headlineContent = { Text(source.name) },
                        supportingContent = { Text(source.mediaTypes.joinToString("/") + if (!source.enabled) " · 已停用" else "") },
                        modifier = Modifier.clickable { onOpenSite(source.id) },
                    )
                }
            }
        }
    }
}
