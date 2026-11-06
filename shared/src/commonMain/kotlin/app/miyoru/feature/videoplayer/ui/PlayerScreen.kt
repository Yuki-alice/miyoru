package app.miyoru.feature.videoplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.RemoteId
import app.miyoru.core.ui.components.MediaRow
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.videoplayer.presentation.PlayerViewModel
import app.miyoru.platform.player.PlayerFacade
import app.miyoru.platform.player.VideoSurface
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "$m:${s.toString().padStart(2, '0')}"
    }
}

/**
 * 播放器：画面区 + 进度 + 播放控制条 + 选集 + 简介（原型 player 屏）。
 * 宽屏把选集放到右侧栏、简介在左下；窄屏整列纵向排列。
 * 三端均为真实播放后端：Android=Media3 ExoPlayer，桌面=mediamp/mpv，iOS=AVPlayer。
 * 后端失败时只在画面区域提示，不替换整页版式。
 */
@Composable
fun PlayerScreen(
    wide: Boolean,
    key: MediaKey,
    unitId: RemoteId?,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel(parameters = { parametersOf(key, unitId) }),
) {
    val facade: PlayerFacade = koinInject()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    val unitLabel = state.units.firstOrNull { it.unitId == state.currentUnitId }
        ?.title?.substringBefore(" · ")
    val currentIndex = state.units.indexOfFirst { it.unitId == state.currentUnitId }
    val canPrev = currentIndex > 0
    val canNext = currentIndex >= 0 && currentIndex < state.units.size - 1

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(
                title = listOfNotNull(state.title, unitLabel).joinToString(" · ").ifBlank { "播放" },
                wide = wide,
                onBack = onBack,
            )
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = pad, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Gap),
                ) {
                    Column(Modifier.weight(1f)) {
                        VideoStage(state.isLoading, state.errorMessage, facade, 420.dp, viewModel)
                        Spacer(Modifier.height(12.dp))
                        PlaybackControls(
                            isPlaying = state.isPlaying,
                            progress = progress,
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            canPrev = canPrev,
                            canNext = canNext,
                            onSeek = { viewModel.seekTo((it * state.durationMs).toLong()) },
                            onToggle = { viewModel.toggle() },
                            onPrev = { if (canPrev) viewModel.selectUnit(state.units[currentIndex - 1].unitId) },
                            onNext = { if (canNext) viewModel.selectUnit(state.units[currentIndex + 1].unitId) },
                        )
                        Spacer(Modifier.height(24.dp))
                        SectionTitle("简介", size = 20)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.intro ?: "该来源未提供简介。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Column(Modifier.width(368.dp)) {
                        SectionTitle("选集", size = 20)
                        Spacer(Modifier.height(8.dp))
                        EpisodeList(
                            units = state.units,
                            currentUnitId = state.currentUnitId,
                            onSelect = viewModel::selectUnit,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = pad, end = pad, top = 8.dp, bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { VideoStage(state.isLoading, state.errorMessage, facade, 214.dp, viewModel) }
                    item {
                        PlaybackControls(
                            isPlaying = state.isPlaying,
                            progress = progress,
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            canPrev = canPrev,
                            canNext = canNext,
                            onSeek = { viewModel.seekTo((it * state.durationMs).toLong()) },
                            onToggle = { viewModel.toggle() },
                            onPrev = { if (canPrev) viewModel.selectUnit(state.units[currentIndex - 1].unitId) },
                            onNext = { if (canNext) viewModel.selectUnit(state.units[currentIndex + 1].unitId) },
                        )
                    }
                    item { SectionTitle("选集", size = 20, modifier = Modifier.padding(top = 8.dp)) }
                    if (state.units.isEmpty()) {
                        item {
                            Text(
                                "该来源没有返回剧集。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(state.units.size) { index ->
                            val unit = state.units[index]
                            MediaRow(
                                title = unit.title,
                                supporting = if (unit.unitId == state.currentUnitId) "正在播放" else null,
                                icon = if (unit.unitId == state.currentUnitId) MiyoruIcons.PlayCircle else MiyoruIcons.Video,
                                onClick = { viewModel.selectUnit(unit.unitId) },
                            )
                        }
                    }
                    item { SectionTitle("简介", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                    item {
                        Text(
                            state.intro ?: "该来源未提供简介。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 画面区：加载/错误都只在区域内提示，保留整体版式。 */
@Composable
private fun VideoStage(
    loading: Boolean,
    error: String?,
    facade: PlayerFacade,
    height: androidx.compose.ui.unit.Dp,
    viewModel: PlayerViewModel,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.inverseSurface),
        contentAlignment = Alignment.Center,
    ) {
        // 始终先挂载画面层：桌面 mpv 需要在 prepare 前拿到渲染上下文，
        // 否则会以 vo=null 起播并直接冲到片尾。
        if (error == null) {
            VideoSurface(player = facade.nativePlayer, modifier = Modifier.fillMaxSize())
        }
        when {
            loading -> Text("加载中…", style = MaterialTheme.typography.bodyMedium, color = colors.inverseOnSurface)
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = colors.inverseOnSurface)
                TextButton(onClick = { viewModel.retry() }) { Text("重试") }
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    canPrev: Boolean,
    canNext: Boolean,
    onSeek: (Float) -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = onSeek,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.surfaceContainerHighest,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(formatTime(durationMs), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, enabled = canPrev) {
                Icon(MiyoruIcons.Prev, contentDescription = "上一集", tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (isPlaying) MiyoruIcons.Pause else MiyoruIcons.Play,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = colors.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onNext, enabled = canNext) {
                Icon(MiyoruIcons.Next, contentDescription = "下一集", tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            IconButton(onClick = {}) {
                Icon(MiyoruIcons.Speed, contentDescription = "倍速", tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = {}) {
                Icon(MiyoruIcons.Fullscreen, contentDescription = "全屏", tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun EpisodeList(
    units: List<app.miyoru.domain.source.model.ContentUnit>,
    currentUnitId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    if (units.isEmpty()) {
        Text("该来源没有返回剧集。", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(units.size) { index ->
            val unit = units[index]
            val playing = unit.unitId == currentUnitId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.RowHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(unit.unitId) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (playing) colors.primaryContainer else colors.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) MiyoruIcons.PlayCircle else MiyoruIcons.Video,
                        contentDescription = null,
                        tint = if (playing) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        unit.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (playing) {
                        Text(
                            "正在播放",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary,
                        )
                    }
                }
            }
        }
    }
}
