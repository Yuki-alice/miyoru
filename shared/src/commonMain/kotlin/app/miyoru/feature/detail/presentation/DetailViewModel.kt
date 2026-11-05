package app.miyoru.feature.detail.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.core.model.MediaKey
import app.miyoru.domain.library.repository.LibraryRepository
import app.miyoru.domain.source.model.ContentUnit
import app.miyoru.domain.source.model.MediaDetail
import app.miyoru.domain.source.repository.SourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val detail: MediaDetail? = null,
    val units: List<ContentUnit> = emptyList(),
    val isFavorite: Boolean = false,
    val errorMessage: String? = null,
)

class DetailViewModel(
    private val key: MediaKey,
    private val sources: SourceRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun toggleFavorite() {
        viewModelScope.launch {
            runCatching { library.toggleFavorite(key) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
                }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = DetailUiState(isLoading = true)
            runCatching {
                val detail = sources.detail(key)
                val units = sources.contentUnits(key)
                val fav = library.observeFavorites().first().any { it.key == key }
                Triple(detail, units, fav)
            }.onSuccess { (detail, units, fav) ->
                _uiState.value = DetailUiState(isLoading = false, detail = detail, units = units, isFavorite = fav)
            }.onFailure {
                _uiState.value = DetailUiState(isLoading = false, errorMessage = it.message)
            }
        }
    }
}
