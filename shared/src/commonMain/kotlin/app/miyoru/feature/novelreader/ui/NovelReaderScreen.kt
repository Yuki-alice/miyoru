package app.miyoru.feature.novelreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.RemoteId
import app.miyoru.feature.novelreader.presentation.NovelReaderViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun NovelReaderScreen(
    key: MediaKey,
    unitId: RemoteId?,
    viewModel: NovelReaderViewModel = koinViewModel(parameters = { parametersOf(key, unitId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.isLoading -> Text("加载中…")
            state.errorMessage != null -> Text("加载失败：${state.errorMessage}")
            else -> {
                Text(state.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "第 ${state.index + 1} 章 / 共 ${state.chapters.size} 章",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    state.text,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.prev() }, enabled = state.index > 0) { Text("上一章") }
                    TextButton(
                        onClick = { viewModel.next() },
                        enabled = state.index < state.chapters.size - 1,
                    ) { Text("下一章") }
                }
            }
        }
    }
}
