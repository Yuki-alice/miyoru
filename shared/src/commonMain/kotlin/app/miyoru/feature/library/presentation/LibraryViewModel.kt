package app.miyoru.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.core.model.MediaType
import app.miyoru.domain.library.repository.LibraryRepository
import app.miyoru.domain.source.model.MediaSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LibraryUiState(
    val isLoading: Boolean = true,
    val items: List<MediaSummary> = emptyList(),
    val selectedType: MediaType? = null,
    val errorMessage: String? = null,
)

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {
    private val filter = MutableStateFlow<MediaType?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = filter.flatMapLatest { type ->
        observe(type)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private fun observe(type: MediaType?): Flow<LibraryUiState> =
        repository.observeFavoriteMedia(type)
            .map<_, LibraryUiState> { LibraryUiState(isLoading = false, items = it, selectedType = type) }
            .catch { emit(LibraryUiState(isLoading = false, selectedType = type, errorMessage = it.message)) }

    fun selectType(type: MediaType?) {
        filter.value = type
    }
}
