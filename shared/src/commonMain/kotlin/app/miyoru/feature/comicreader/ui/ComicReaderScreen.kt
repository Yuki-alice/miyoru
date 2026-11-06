package app.miyoru.feature.comicreader.ui

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.RemoteId
import app.miyoru.core.ui.components.EmptyState
import app.miyoru.core.ui.components.MiyoruProgress
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.comicreader.presentation.ComicReaderViewModel
import app.miyoru.platform.image.CoilImageRequestFactory
import app.miyoru.platform.image.ImageSource
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 漫画阅读器：横向翻页 + 双指缩放；底部页码进度与翻章按钮。
 * 沉浸页不显示侧栏，返回入口在顶栏。
 */
@Composable
fun ComicReaderScreen(
    wide: Boolean,
    key: MediaKey,
    unitId: RemoteId?,
    onBack: () -> Unit,
    viewModel: ComicReaderViewModel = koinViewModel(parameters = { parametersOf(key, unitId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { state.pages.size })
    LaunchedEffect(state.pageIndex) {
        if (pagerState.currentPage != state.pageIndex) pagerState.scrollToPage(state.pageIndex)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { viewModel.setPage(it) }
    }
    val progress = if (state.pages.isEmpty()) 0f else (state.pageIndex + 1f) / state.pages.size

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(
                title = buildString {
                    append(state.title.ifBlank { "阅读" })
                    if (state.pages.isNotEmpty()) append(" · ${state.pageIndex + 1}/${state.pages.size}")
                },
                wide = wide,
                onBack = onBack,
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", style = MaterialTheme.typography.bodyMedium)
                }
                state.errorMessage != null -> EmptyState(
                    icon = MiyoruIcons.Comic,
                    title = "图片加载失败",
                    hint = state.errorMessage ?: "稍后重试或换一个来源。",
                    modifier = Modifier.fillMaxWidth(),
                )
                state.pages.isEmpty() -> EmptyState(
                    icon = MiyoruIcons.Comic,
                    title = "没有页面",
                    hint = "该章节还没有返回图片。",
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact),
                    ) {
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                            val page = state.pages[index]
                            var scale by remember(index) { mutableStateOf(1f) }
                            var offsetX by remember(index) { mutableStateOf(0f) }
                            var offsetY by remember(index) { mutableStateOf(0f) }
                            val context = LocalPlatformContext.current
                            val headersFactory = remember { CoilImageRequestFactory() }
                            val model = remember(page) {
                                if (page.bytes != null) {
                                    page.bytes
                                } else {
                                    val nh = headersFactory.networkHeaders(ImageSource(page.url ?: "", page.headers))
                                    ImageRequest.Builder(context).data(page.url ?: "").httpHeaders(nh).build()
                                }
                            }
                            Box(Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = model,
                                    contentDescription = "第 ${index + 1} 页",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            translationX = offsetX,
                                            translationY = offsetY,
                                        )
                                        .pointerInput(index) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                scale = (scale * zoom).coerceIn(1f, 5f)
                                                if (scale > 1f) {
                                                    offsetX += pan.x
                                                    offsetY += pan.y
                                                } else {
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                }
                                            }
                                        },
                                )
                            }
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact,
                                end = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact,
                            ),
                    ) {
                        Spacer(Modifier.height(12.dp))
                        MiyoruProgress(value = progress)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { viewModel.prevPage() }, enabled = state.pageIndex > 0) {
                                Icon(
                                    MiyoruIcons.Prev,
                                    contentDescription = "上一页",
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${state.pageIndex + 1} / ${state.pages.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.nextPage() },
                                enabled = state.pageIndex < state.pages.size - 1,
                            ) {
                                Icon(
                                    MiyoruIcons.Next,
                                    contentDescription = "下一页",
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
