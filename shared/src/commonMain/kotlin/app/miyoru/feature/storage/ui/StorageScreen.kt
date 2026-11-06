package app.miyoru.feature.storage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.ui.components.MediaRow
import app.miyoru.core.ui.components.MiyoruButton
import app.miyoru.core.ui.components.MiyoruButtonVariant
import app.miyoru.core.ui.components.MiyoruProgress
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.storage.presentation.StorageViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * 数据与存储：缓存占用与清理（已落地）、下载位置与备份（P1 显示为未启用）。
 */
@Composable
fun StorageScreen(
    wide: Boolean,
    onBack: () -> Unit,
    viewModel: StorageViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    val mb = state.cacheBytes / (1024.0 * 1024.0)
    val limitMb = 8192.0
    val progress = (state.cacheBytes / (limitMb * 1024 * 1024)).toFloat().coerceIn(0f, 1f)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(title = "数据与存储", wide = wide, onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { SectionTitle("存储", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "缓存 ${formatMb(mb)} · 上限 8 GB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        MiyoruProgress(value = progress)
                    }
                }
                item {
                    MediaRow(
                        title = "图片与正文缓存",
                        supporting = "${formatMb(mb)} · 可随时清理，不影响书架与进度",
                        icon = MiyoruIcons.Image,
                        onClick = { viewModel.clearCache() },
                        trailing = { Text("清除", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) },
                    )
                }
                item {
                    MediaRow(
                        title = "下载位置",
                        supporting = "应用数据目录 / downloads",
                        icon = MiyoruIcons.Folder,
                    )
                }
                item {
                    MediaRow(
                        title = "自动清理",
                        supporting = "超过上限时清理最久未读内容（P1）",
                        icon = MiyoruIcons.Clean,
                        trailing = { Text("未启用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }

                item { SectionTitle("备份", size = 18, modifier = Modifier.padding(top = 12.dp)) }
                item {
                    MediaRow(
                        title = "创建备份",
                        supporting = "包含书架、进度与设置（P1）",
                        icon = MiyoruIcons.CloudUpload,
                        trailing = { Text("未启用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }
                item {
                    MediaRow(
                        title = "恢复备份",
                        supporting = "从本地文件导入（P1）",
                        icon = MiyoruIcons.Restore,
                        trailing = { Text("未启用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }
                item {
                    MiyoruButton(
                        label = "清除缓存",
                        onClick = { viewModel.clearCache() },
                        icon = MiyoruIcons.Clean,
                        variant = MiyoruButtonVariant.Outlined,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (state.cleared) {
                    item {
                        Text(
                            "缓存已清理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                item { PageFooter() }
            }
        }
    }
}

private fun formatMb(mb: Double): String {
    if (mb >= 1024) {
        val tenths = (mb / 1024 * 10).toInt()
        return "${tenths / 10}.${tenths % 10} GB"
    }
    return "${mb.toInt()} MB"
}
