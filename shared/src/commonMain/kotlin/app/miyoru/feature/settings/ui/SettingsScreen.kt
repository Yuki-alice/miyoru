package app.miyoru.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.miyoru.core.ui.components.MiyoruTopBar
import app.miyoru.core.ui.components.PageFooter
import app.miyoru.core.ui.components.SectionTitle
import app.miyoru.core.ui.components.SettingRow
import app.miyoru.core.ui.icon.MiyoruIcons
import app.miyoru.core.ui.theme.Dimens
import app.miyoru.domain.settings.repository.SettingsRepository
import app.miyoru.feature.settings.presentation.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

private val THEME_LABELS = mapOf(
    SettingsRepository.THEME_SYSTEM to "跟随系统",
    SettingsRepository.THEME_LIGHT to "浅色",
    SettingsRepository.THEME_DARK to "深色",
)
private val THEME_ORDER = listOf(
    SettingsRepository.THEME_SYSTEM,
    SettingsRepository.THEME_DARK,
    SettingsRepository.THEME_LIGHT,
)

/**
 * 设置：外观 / 阅读与播放 / 通知三组，点击行循环切换取值（原型 settings 屏）。
 * 仅展示已真实持久化的项；未实现的开关不放假控件。
 */
@Composable
fun SettingsScreen(
    wide: Boolean,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pad = if (wide) Dimens.PagePadding else Dimens.PagePaddingCompact

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MiyoruTopBar(title = "设置", wide = wide, onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { SectionTitle("外观", size = 18, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    SettingRow(
                        label = "主题",
                        supporting = "深色为设计基准，可跟随系统",
                        icon = MiyoruIcons.DarkMode,
                        trailingText = THEME_LABELS[state.themeMode] ?: state.themeMode,
                        onClick = {
                            val next = THEME_ORDER[(THEME_ORDER.indexOf(state.themeMode) + 1).mod(THEME_ORDER.size)]
                            viewModel.setTheme(next)
                        },
                    )
                }
                item {
                    SettingRow(
                        label = "封面动态取色",
                        supporting = "强调色取自作品封面（P1）",
                        icon = MiyoruIcons.Palette,
                        trailingText = "未启用",
                    )
                }
                item {
                    SettingRow(
                        label = "界面语言",
                        supporting = "当前仅简体中文",
                        icon = MiyoruIcons.Translate,
                        trailingText = "简体中文",
                    )
                }

                item { SectionTitle("阅读与播放", size = 18, modifier = Modifier.padding(top = 12.dp)) }
                item {
                    SettingRow(
                        label = "默认内容类型",
                        supporting = "打开应用时优先展示的类型",
                        icon = MiyoruIcons.Comic,
                        trailingText = state.defaultMediaType,
                        onClick = {
                            val order = listOf("NOVEL", "COMIC", "VIDEO")
                            val next = order[(order.indexOf(state.defaultMediaType) + 1).mod(order.size)]
                            viewModel.setDefaultType(next)
                        },
                    )
                }
                item {
                    SettingRow(
                        label = "并发下载数",
                        supporting = "同时进行的下载任务上限",
                        icon = MiyoruIcons.Tune,
                        trailingText = "${state.concurrency}",
                        onClick = { viewModel.setConcurrency(if (state.concurrency >= 5) 1 else state.concurrency + 1) },
                    )
                }
                item {
                    SettingRow(
                        label = "自动续播",
                        supporting = "从上次时间点继续（播放器已默认记忆）",
                        icon = MiyoruIcons.PlayCircle,
                        trailingText = "已开启",
                    )
                }

                item { SectionTitle("通知", size = 18, modifier = Modifier.padding(top = 12.dp)) }
                item {
                    SettingRow(
                        label = "追更提醒",
                        supporting = "订阅作品更新时通知（P1）",
                        icon = MiyoruIcons.Bell,
                        trailingText = "未启用",
                    )
                }
                item { PageFooter() }
            }
        }
    }
}
