package app.miyoru.feature.site.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.SourceId
import app.miyoru.domain.source.model.MediaSummary
import app.miyoru.domain.source.usecase.SearchAcrossSources
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SiteUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val items: List<MediaSummary> = emptyList(),
    val errorMessage: String? = null,
)

class SiteViewModel(
    private val sourceId: SourceId,
    private val search: SearchAcrossSources,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SiteUiState())
    val uiState: StateFlow<SiteUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun submit(type: MediaType = MediaType.NOVEL) {
        val q = _uiState.value.query
        if (q.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, errorMessage = null)
            runCatching {
                search(type, q, listOf(sourceId)).results.firstOrNull()
            }.onSuccess { r ->
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    items = r?.items ?: emptyList(),
                    errorMessage = r?.error?.message,
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSearching = false, errorMessage = it.message)
            }
        }
    }
}
