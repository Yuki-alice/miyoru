package app.miyoru.feature.sources.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.domain.plugin.model.InstalledPlugin
import app.miyoru.domain.plugin.repository.PluginRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SourcesUiState(
    val isLoading: Boolean = true,
    val plugins: List<InstalledPlugin> = emptyList(),
    val errorMessage: String? = null,
)

class SourcesViewModel(private val repository: PluginRepository) : ViewModel() {
    val uiState: StateFlow<SourcesUiState> = repository.observeInstalled()
        .map<_, SourcesUiState> { SourcesUiState(isLoading = false, plugins = it) }
        .catch { emit(SourcesUiState(isLoading = false, errorMessage = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SourcesUiState())

    fun setEnabled(pluginId: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setEnabled(pluginId, enabled) }
        }
    }

    fun uninstall(pluginId: String) {
        viewModelScope.launch {
            runCatching { repository.uninstall(pluginId) }
        }
    }
}
