package app.miyoru.feature.localimport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.ui.components.MiyoruButton
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.localimport.presentation.ImportViewModel
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * 本地导入：选择 TXT / EPUB / CBZ，校验后进入书架（不在 P0 之外的格式范围内）。
 */
@Composable
fun ImportScreen(
    wide: Boolean,
    onBack: () -> Unit,
    viewModel: ImportViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    val colors = MaterialTheme.colorScheme
    val picker = rememberFilePickerLauncher(
        type = FileKitType.File("txt", "epub", "cbz"),
        title = "选择 TXT / EPUB / CBZ 文件",
    ) { file ->
        if (file != null) {
            scope.launch { runCatching { viewModel.importPicked(file.name, file.readBytes()) } }
        }
    }

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(title = "本地导入", wide = wide, onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceContainerLow)
                            .padding(24.dp),
                    ) {
                        Text("导入本地文件", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "支持 TXT / EPUB 小说与 CBZ 漫画。导入后进入书架，与源内容共用收藏、历史与进度。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        MiyoruButton(
                            label = if (state.isImporting) "导入中…" else "选择文件",
                            onClick = { picker.launch() },
                            icon = MiyoruIcons.Import,
                        )
                    }
                }
                state.message?.let { message ->
                    item {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.isError) colors.error else colors.primary,
                        )
                    }
                }
                state.lastImported?.let { imported ->
                    item {
                        Text(
                            "已加入书架：${imported.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                        )
                    }
                }
                item { SectionTitle("支持的格式", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    Text(
                        "TXT：按章节标题切分；EPUB：解析 OPF 与 spine；CBZ：按文件名自然排序的图片包。" +
                            "解析失败会给出原因，不影响已有书架内容。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                item { PageFooter() }
            }
        }
    }
}
