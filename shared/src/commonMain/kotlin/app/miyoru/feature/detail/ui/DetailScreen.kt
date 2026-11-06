package app.miyoru.feature.detail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.ui.components.Cover
import app.miyoru.core.ui.components.EmptyState
import app.miyoru.core.ui.components.MediaRow
import app.miyoru.core.ui.components.MiyoruButton
import app.miyoru.core.ui.components.MiyoruButtonVariant
import app.miyoru.core.ui.components.MiyoruChips
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.components.icon
import app.miyoru.core.ui.components.label
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.detail.presentation.DetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 作品详情：宽屏左封面右信息、目录通栏；窄屏封面与标题并在首行。
 * 沉浸页不显示侧栏，返回入口在顶栏。
 */
@Composable
fun DetailScreen(
    wide: Boolean,
    key: MediaKey,
    onBack: () -> Unit,
    onOpenReader: (unitId: RemoteId?) -> Unit,
    viewModel: DetailViewModel = koinViewModel(parameters = { parametersOf(key) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(
                title = "作品详情",
                wide = wide,
                onBack = onBack,
                actions = {
                    IconButton(onClick = {}) {
                        Icon(MiyoruIcons.MoreVert, contentDescription = "更多", modifier = Modifier.size(22.dp))
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
            ) {
                when {
                    state.isLoading -> item {
                        Text("加载中…", style = MaterialTheme.typography.bodyMedium)
                    }
                    state.errorMessage != null -> item {
                        EmptyState(
                            icon = MiyoruIcons.Info,
                            title = "详情加载失败",
                            hint = state.errorMessage ?: "源暂时不可用，请稍后重试。",
                            action = { TextButton(onClick = { viewModel.retry() }) { Text("重试") } },
                        )
                    }
                    state.detail != null -> {
                        val detail = state.detail!!
                        item {
                            val meta = buildMeta(detail.summary.author, key.mediaType, detail.sourceName, detail.attributes)
                            val tags = tagsOf(detail.attributes, key.mediaType)
                            val intro = introOf(detail.attributes)
                            if (wide) {
                                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                                    Cover(
                                        url = detail.summary.coverUrl,
                                        type = key.mediaType,
                                        modifier = Modifier
                                            .width(280.dp)
                                            .height(392.dp)
                                            .clip(RoundedCornerShape(16.dp)),
                                    )
                                    DetailInfo(
                                        title = detail.summary.title,
                                        meta = meta,
                                        tags = tags,
                                        intro = intro,
                                        isFavorite = state.isFavorite,
                                        onStart = { onOpenReader(null) },
                                        onToggleFavorite = { viewModel.toggleFavorite() },
                                        compact = false,
                                        startIcon = key.mediaType.icon(),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Cover(
                                        url = detail.summary.coverUrl,
                                        type = key.mediaType,
                                        modifier = Modifier
                                            .width(180.dp)
                                            .height(250.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            detail.summary.title,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            meta,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (state.isFavorite) {
                                            Spacer(Modifier.height(10.dp))
                                            MiyoruChips(labels = listOf("已追更"), selected = 0, onSelect = {})
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                DetailInfo(
                                    title = null,
                                    meta = null,
                                    tags = tags,
                                    intro = intro,
                                    isFavorite = state.isFavorite,
                                    onStart = { onOpenReader(null) },
                                    onToggleFavorite = { viewModel.toggleFavorite() },
                                    compact = true,
                                    showFavoriteChip = false,
                                    startIcon = key.mediaType.icon(),
                                )
                            }
                        }
                        item {
                            SectionTitle(
                                "目录 · 共 ${state.units.size} ${unitNoun(key.mediaType)}",
                                size = if (wide) 20 else 18,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (state.units.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = MiyoruIcons.Chapters,
                                    title = "暂无目录",
                                    hint = "该来源还没有返回章节或剧集，稍后再试。",
                                )
                            }
                        } else {
                            items(state.units.size) { index ->
                                val unit = state.units[index]
                                MediaRow(
                                    title = unit.title,
                                    supporting = null,
                                    icon = if (key.mediaType == MediaType.VIDEO) MiyoruIcons.PlayCircle else MiyoruIcons.Chapters,
                                    onClick = { onOpenReader(RemoteId(unit.unitId)) },
                                )
                            }
                        }
                    }
                }
                item { PageFooter() }
            }
        }
    }
}

@Composable
private fun DetailInfo(
    title: String?,
    meta: String?,
    tags: List<String>,
    intro: String?,
    isFavorite: Boolean,
    onStart: () -> Unit,
    onToggleFavorite: () -> Unit,
    compact: Boolean,
    startIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    showFavoriteChip: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        if (title != null) {
            Text(
                title,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (meta != null) {
            Text(meta, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
        if (tags.isNotEmpty()) {
            MiyoruChips(labels = tags, selected = -1, onSelect = {})
        }
        if (isFavorite && showFavoriteChip) {
            Spacer(Modifier.height(8.dp))
            MiyoruChips(labels = listOf("已追更"), selected = 0, onSelect = {})
        }
        Spacer(Modifier.height(20.dp))
        if (!intro.isNullOrBlank()) {
            SectionTitle("简介", size = 20)
            Spacer(Modifier.height(8.dp))
            Text(
                intro,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(20.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MiyoruButton(
                label = "开始阅读",
                onClick = onStart,
                icon = startIcon,
                modifier = if (compact) Modifier.weight(1f) else Modifier.width(240.dp),
            )
            MiyoruButton(
                label = if (isFavorite) "已在书架" else "加入书架",
                onClick = onToggleFavorite,
                icon = MiyoruIcons.BookmarkAdd,
                variant = MiyoruButtonVariant.Tonal,
                modifier = if (compact) Modifier.weight(1f) else Modifier.width(240.dp),
            )
        }
    }
}

private fun buildMeta(author: String?, type: MediaType, sourceName: String, attributes: Map<String, String>): String {
    val parts = mutableListOf<String>()
    parts += type.label()
    author?.takeIf { it.isNotBlank() }?.let { parts += it }
    val status = attributes["status"] ?: attributes["update"] ?: attributes["latest"]
    status?.takeIf { it.isNotBlank() }?.let { parts += it }
    if (sourceName.isNotBlank()) parts += "来源 $sourceName"
    return parts.joinToString(" · ")
}

private fun tagsOf(attributes: Map<String, String>, type: MediaType): List<String> {
    val raw = attributes["tags"] ?: attributes["genres"] ?: ""
    val tags = raw.split(',', '，', '/', '|').map { it.trim() }.filter { it.isNotBlank() }.take(3)
    return tags.ifEmpty { listOf(type.label()) }
}

private fun introOf(attributes: Map<String, String>): String? =
    attributes["intro"] ?: attributes["description"] ?: attributes["summary"] ?: attributes["desc"]

private fun unitNoun(type: MediaType): String = if (type == MediaType.VIDEO) "集" else "话"
