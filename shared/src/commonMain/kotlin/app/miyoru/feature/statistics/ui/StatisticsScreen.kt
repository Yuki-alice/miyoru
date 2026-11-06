package app.miyoru.feature.statistics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.ui.components.MediaGrid
import app.miyoru.core.ui.components.MediaRow
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.components.StatCard
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.feature.statistics.presentation.StatisticsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * 统计：本月收藏与历史条目、按类型的数量与占比（原型 statistics 屏）。
 * 时长类指标依赖播放/阅读时长上报（P1），当前只呈现本地可得的计数。
 */
@Composable
fun StatisticsScreen(
    wide: Boolean,
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact
    val total = state.comicsCount + state.novelsCount + state.videosCount
    fun pct(n: Int) = if (total == 0) "0%" else "${n * 100 / total}%"

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(title = "统计", wide = wide, onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
            ) {
                item { SectionTitle("本月概览", size = 20, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    val cardHeight = if (wide) 140.dp else 120.dp
                    val cards = listOf(
                        Triple("${state.favoritesCount} 部", "书架收藏", MiyoruIcons.Library),
                        Triple("${state.comicsCount} 部", "漫画", MiyoruIcons.Comic),
                        Triple("${state.novelsCount} 部", "小说", MiyoruIcons.Novel),
                        Triple("${state.videosCount} 部", "视频", MiyoruIcons.Video),
                    )
                    MediaGrid(
                        items = cards,
                        columns = if (wide) 4 else 2,
                        gap = Dimens.Gap,
                        cardHeight = cardHeight,
                    ) { (value, label, icon) ->
                        StatCard(
                            value = value,
                            label = label,
                            icon = icon,
                            modifier = Modifier.fillMaxWidth(),
                            height = cardHeight,
                        )
                    }
                }
                item { SectionTitle("内容分布", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    MediaRow(
                        title = "漫画",
                        supporting = "占比 ${pct(state.comicsCount)} · ${state.comicsCount} 部",
                        icon = MiyoruIcons.Comic,
                    )
                }
                item {
                    MediaRow(
                        title = "小说",
                        supporting = "占比 ${pct(state.novelsCount)} · ${state.novelsCount} 部",
                        icon = MiyoruIcons.Novel,
                    )
                }
                item {
                    MediaRow(
                        title = "视频",
                        supporting = "占比 ${pct(state.videosCount)} · ${state.videosCount} 部",
                        icon = MiyoruIcons.Video,
                    )
                }
                item { PageFooter() }
            }
        }
    }
}
