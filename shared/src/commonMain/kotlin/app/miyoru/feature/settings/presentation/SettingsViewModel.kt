package app.miyoru.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.domain.settings.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: String = SettingsRepository.THEME_SYSTEM,
    val defaultMediaType: String = "NOVEL",
    val concurrency: Int = 3,
)

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        repo.observeThemeMode(), repo.observeDefaultMediaType(), repo.observeConcurrency(),
    ) { theme, type, concurrency -> SettingsUiState(theme, type, concurrency) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(mode: String) = viewModelScope.launch { runCatching { repo.setThemeMode(mode) } }
    fun setDefaultType(type: String) = viewModelScope.launch { runCatching { repo.setDefaultMediaType(type) } }
    fun setConcurrency(n: Int) = viewModelScope.launch { runCatching { repo.setConcurrency(n) } }
}
