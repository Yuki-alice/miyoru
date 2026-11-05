package app.miyoru.feature.comicreader.ui

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.RemoteId
import app.miyoru.feature.comicreader.presentation.ComicReaderViewModel
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import app.miyoru.platform.image.CoilImageRequestFactory
import app.miyoru.platform.image.ImageSource
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ComicReaderScreen(
    key: MediaKey,
    unitId: RemoteId?,
    viewModel: ComicReaderViewModel = koinViewModel(parameters = { parametersOf(key, unitId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { state.pages.size })
    LaunchedEffect(state.pageIndex) {
        if (pagerState.currentPage != state.pageIndex) pagerState.scrollToPage(state.pageIndex)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { viewModel.setPage(it) }
    }
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            state.isLoading -> Text("加载中…")
            state.errorMessage != null -> Text("加载失败：${state.errorMessage}")
            state.pages.isEmpty() -> Text("没有页面。")
            else -> {
                Text(
                    "${state.title} · ${state.pageIndex + 1}/${state.pages.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
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
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.prevPage() }, enabled = state.pageIndex > 0) { Text("上一页") }
                    TextButton(onClick = { viewModel.nextPage() }, enabled = state.pageIndex < state.pages.size - 1) { Text("下一页") }
                }
            }
        }
    }
}
