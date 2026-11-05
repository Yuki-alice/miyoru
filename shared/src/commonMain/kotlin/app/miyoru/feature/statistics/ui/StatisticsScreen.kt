package app.miyoru.feature.statistics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.feature.statistics.presentation.StatisticsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("统计", style = MaterialTheme.typography.headlineSmall)
        if (state.isLoading) {
            Text("加载中…")
        } else {
            ListItem(headlineContent = { Text("收藏总数") }, trailingContent = { Text("${state.favoritesCount}") })
            ListItem(headlineContent = { Text("小说 / 漫画 / 视频") }, trailingContent = { Text("${state.novelsCount} / ${state.comicsCount} / ${state.videosCount}") })
            ListItem(headlineContent = { Text("历史记录") }, trailingContent = { Text("${state.historyCount}") })
            ListItem(headlineContent = { Text("进行中下载") }, trailingContent = { Text("${state.runningDownloads}") })
        }
    }
}
