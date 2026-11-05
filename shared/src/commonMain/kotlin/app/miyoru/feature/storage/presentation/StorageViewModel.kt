package app.miyoru.feature.storage.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.domain.cache.repository.CacheRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StorageUiState(
    val isLoading: Boolean = true,
    val cacheBytes: Long = 0,
    val cleared: Boolean = false,
)

class StorageViewModel(private val cache: CacheRepository) : ViewModel() {
    val uiState: StateFlow<StorageUiState> = cache.observeSizeBytes()
        .map<_, StorageUiState> { StorageUiState(isLoading = false, cacheBytes = it) }
        .catch { emit(StorageUiState(isLoading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageUiState())

    fun clearCache() {
        viewModelScope.launch {
            runCatching { cache.clear() }
        }
    }
}
