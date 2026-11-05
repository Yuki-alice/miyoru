package app.miyoru.feature.statistics.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.domain.download.repository.DownloadRepository
import app.miyoru.domain.library.repository.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val favoritesCount: Int = 0,
    val novelsCount: Int = 0,
    val comicsCount: Int = 0,
    val videosCount: Int = 0,
    val historyCount: Int = 0,
    val runningDownloads: Int = 0,
)

class StatisticsViewModel(
    library: LibraryRepository,
    downloads: DownloadRepository,
) : ViewModel() {
    val uiState: StateFlow<StatisticsUiState> = combine(
        library.observeFavorites(), library.observeHistory(10_000), downloads.observeTasks(),
    ) { favs, history, tasks ->
        StatisticsUiState(
            isLoading = false,
            favoritesCount = favs.size,
            novelsCount = favs.count { it.key.mediaType.name == "NOVEL" },
            comicsCount = favs.count { it.key.mediaType.name == "COMIC" },
            videosCount = favs.count { it.key.mediaType.name == "VIDEO" },
            historyCount = history.size,
            runningDownloads = tasks.count { it.state == "RUNNING" || it.state == "QUEUED" },
        )
    }.catch { emit(StatisticsUiState(isLoading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())
}
