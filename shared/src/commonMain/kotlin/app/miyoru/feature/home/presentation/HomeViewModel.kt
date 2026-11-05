package app.miyoru.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.domain.library.repository.LibraryRepository
import app.miyoru.domain.source.model.MediaSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val isLoading: Boolean = true,
    val continueReading: List<MediaSummary> = emptyList(),
    val favoritesCount: Int = 0,
    val errorMessage: String? = null,
)

class HomeViewModel(library: LibraryRepository) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        library.observeHistoryMedia(6),
        library.observeFavorites(),
    ) { history, favorites ->
        HomeUiState(isLoading = false, continueReading = history, favoritesCount = favorites.size)
    }.catch { emit(HomeUiState(isLoading = false, errorMessage = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
