package app.miyoru.feature.videoplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.RemoteId
import app.miyoru.feature.videoplayer.presentation.PlayerViewModel
import app.miyoru.platform.player.PlayerFacade
import app.miyoru.platform.player.VideoSurface
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PlayerScreen(
    key: MediaKey,
    unitId: RemoteId?,
    viewModel: PlayerViewModel = koinViewModel(parameters = { parametersOf(key, unitId) }),
) {
    val facade: PlayerFacade = koinInject()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.isLoading -> Text("加载中…")
            state.errorMessage != null -> {
                Text("播放失败：${state.errorMessage}")
                TextButton(onClick = { viewModel.retry() }) { Text("重试") }
            }
            else -> {
                Text(state.title, style = MaterialTheme.typography.headlineSmall)
                VideoSurface(player = facade.nativePlayer, modifier = Modifier.fillMaxWidth().height(240.dp))
                val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
                Slider(
                    value = progress,
                    onValueChange = { viewModel.seekTo((it * state.durationMs).toLong()) },
                )
                Text("${state.positionMs / 1000}s / ${state.durationMs / 1000}s")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.toggle() }) {
                        Text(if (state.isPlaying) "暂停" else "播放")
                    }
                }
            }
        }
    }
}
