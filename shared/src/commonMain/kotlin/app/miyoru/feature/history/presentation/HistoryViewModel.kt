package app.miyoru.feature.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.core.model.MediaKey
import app.miyoru.domain.library.repository.LibraryRepository
import app.miyoru.domain.source.model.MediaSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoading: Boolean = true,
    val items: List<MediaSummary> = emptyList(),
    val errorMessage: String? = null,
)

class HistoryViewModel(private val repository: LibraryRepository) : ViewModel() {
    val uiState: StateFlow<HistoryUiState> = repository.observeHistoryMedia(100)
        .map<_, HistoryUiState> { HistoryUiState(isLoading = false, items = it) }
        .catch { emit(HistoryUiState(isLoading = false, errorMessage = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun remove(key: MediaKey) {
        viewModelScope.launch {
            runCatching { repository.removeHistory(key) }
        }
    }
}
