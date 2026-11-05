package app.miyoru.feature.browse.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.domain.source.model.SourceInfo
import app.miyoru.domain.source.repository.SourceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BrowseUiState(
    val isLoading: Boolean = true,
    val sources: List<SourceInfo> = emptyList(),
    val errorMessage: String? = null,
)

class BrowseViewModel(sources: SourceRepository) : ViewModel() {
    val uiState: StateFlow<BrowseUiState> = sources.observeSources()
        .map<_, BrowseUiState> { BrowseUiState(isLoading = false, sources = it) }
        .catch { emit(BrowseUiState(isLoading = false, errorMessage = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState())
}
