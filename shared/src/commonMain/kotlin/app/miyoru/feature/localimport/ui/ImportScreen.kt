package app.miyoru.feature.localimport.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.feature.localimport.presentation.ImportViewModel
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ImportScreen(viewModel: ImportViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val picker = rememberFilePickerLauncher(
        type = FileKitType.File("txt", "epub", "cbz"),
        title = "选择 TXT / EPUB / CBZ 文件",
    ) { file ->
        if (file != null) {
            scope.launch {
                runCatching { viewModel.importPicked(file.name, file.readBytes()) }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("本地导入", style = MaterialTheme.typography.headlineSmall)
        Text("支持 TXT / EPUB 小说与 CBZ 漫画，导入后进入书架统一管理。")
        Button(onClick = { picker.launch() }, enabled = !state.isImporting) {
            Text(if (state.isImporting) "导入中…" else "选择文件")
        }
        state.message?.let {
            Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        state.lastImported?.let {
            Text("已加入书架：${it.title}")
        }
    }
}
