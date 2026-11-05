package app.miyoru.feature.categories.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.core.model.MediaType
import app.miyoru.domain.library.repository.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CategoryEntry(val type: MediaType, val count: Int)

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val entries: List<CategoryEntry> = emptyList(),
)

class CategoriesViewModel(library: LibraryRepository) : ViewModel() {
    val uiState: StateFlow<CategoriesUiState> = library.observeFavorites()
        .map<_, CategoriesUiState> { favs ->
            CategoriesUiState(
                isLoading = false,
                entries = MediaType.entries.map { t -> CategoryEntry(t, favs.count { it.key.mediaType == t }) },
            )
        }
        .catch { emit(CategoriesUiState(isLoading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())
}
