package app.miyoru.feature.extensions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.feature.extensions.presentation.ExtensionsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ExtensionsScreen(viewModel: ExtensionsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("插件商店", style = MaterialTheme.typography.headlineSmall)
        Text("粘贴插件 ZIP 下载地址安装（仅安装你信任的来源）。", style = MaterialTheme.typography.bodyMedium)
        TextField(
            value = state.url,
            onValueChange = { viewModel.updateUrl(it) },
            label = { Text("插件包 URL（https）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { viewModel.install() }, enabled = !state.isInstalling) {
            Text(if (state.isInstalling) "安装中…" else "安装")
        }
        state.message?.let {
            Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }
}
