package app.miyoru.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.domain.settings.repository.SettingsRepository
import app.miyoru.feature.settings.presentation.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        Text("外观", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                SettingsRepository.THEME_SYSTEM to "跟随系统",
                SettingsRepository.THEME_LIGHT to "浅色",
                SettingsRepository.THEME_DARK to "深色",
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = state.themeMode == mode,
                    onClick = { viewModel.setTheme(mode) },
                    label = { Text(label) },
                )
            }
        }
        Text("默认媒体类型", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("NOVEL" to "小说", "COMIC" to "漫画", "VIDEO" to "视频").forEach { (type, label) ->
                FilterChip(
                    selected = state.defaultMediaType == type,
                    onClick = { viewModel.setDefaultType(type) },
                    label = { Text(label) },
                )
            }
        }
        Text("下载并发数：${state.concurrency}", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(1, 2, 4, 8).forEach { n ->
                FilterChip(
                    selected = state.concurrency == n,
                    onClick = { viewModel.setConcurrency(n) },
                    label = { Text("$n") },
                )
            }
        }
        Text("并发取值范围 1-8，超出会被钳制。", style = MaterialTheme.typography.bodySmall)
    }
}
