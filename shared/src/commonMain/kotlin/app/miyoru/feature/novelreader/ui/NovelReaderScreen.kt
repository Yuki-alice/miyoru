package app.miyoru.feature.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.RemoteId
import app.miyoru.core.ui.components.EmptyState
import app.miyoru.core.ui.components.MiyoruProgress
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.novelreader.presentation.NovelReaderViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 小说阅读器：宽屏正文限宽 720dp 居中，底部进度与翻章工具条。
 * 进度为章节粒度，切章即保存（见 ViewModel）。
 */
@Composable
fun NovelReaderScreen(
    wide: Boolean,
    key: MediaKey,
    unitId: RemoteId?,
    onBack: () -> Unit,
    viewModel: NovelReaderViewModel = koinViewModel(parameters = { parametersOf(key, unitId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val progress = if (state.chapters.isEmpty()) 0f else (state.index + 1f) / state.chapters.size
    val chapter = state.chapters.getOrNull(state.index)
    val topTitle = buildString {
        if (chapter != null) append("第 ${state.index + 1} 章")
        if (state.title.isNotBlank()) {
            if (chapter != null) append(" · ")
            append(state.title)
        }
        if (isEmpty()) append("阅读")
    }

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(title = topTitle, wide = wide, onBack = onBack)
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact),
            ) {
                val contentWidth = if (wide) minOf(720.dp, maxWidth) else maxWidth
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载中…", style = MaterialTheme.typography.bodyMedium)
                    }
                    state.errorMessage != null -> EmptyState(
                        icon = MiyoruIcons.Novel,
                        title = "正文加载失败",
                        hint = state.errorMessage ?: "稍后重试或换一个来源。",
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    else -> Column(Modifier.align(Alignment.TopCenter).width(contentWidth).fillMaxHeight()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceContainerLow)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = if (wide) 32.dp else 20.dp, vertical = 28.dp),
                        ) {
                            Text(
                                chapter?.title ?: state.title.ifBlank { "第 ${state.index + 1} 章" },
                                fontSize = if (wide) 24.sp else 20.sp,
                                lineHeight = if (wide) 32.sp else 28.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = colors.onSurface,
                            )
                            Spacer(Modifier.height(20.dp))
                            Text(
                                state.text.ifBlank { "（本章暂无正文）" },
                                fontSize = 16.sp,
                                lineHeight = 30.sp,
                                color = colors.onSurface.copy(alpha = 0.92f),
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        MiyoruProgress(value = progress)
                        Spacer(Modifier.height(8.dp))
                        ReaderToolbar(
                            canPrev = state.index > 0,
                            canNext = state.index < state.chapters.size - 1,
                            onPrev = { viewModel.prev() },
                            onNext = { viewModel.next() },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderToolbar(canPrev: Boolean, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint: (Boolean) -> androidx.compose.ui.graphics.Color = { enabled ->
            if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.35f)
        }
        IconButton(onClick = onPrev, enabled = canPrev) {
            Icon(MiyoruIcons.Prev, contentDescription = "上一章", tint = tint(canPrev), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = {}) {
            Icon(MiyoruIcons.Chapters, contentDescription = "目录", tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = {}) {
            Icon(MiyoruIcons.TextStyle, contentDescription = "阅读设置", tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(MiyoruIcons.Next, contentDescription = "下一章", tint = tint(canNext), modifier = Modifier.size(22.dp))
        }
    }
}
