package app.miyoru.feature.extensions.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.data.plugin.InstallPluginFromUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExtensionsUiState(
    val url: String = "",
    val isInstalling: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

class ExtensionsViewModel(private val installer: InstallPluginFromUrl) : ViewModel() {
    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(url = url, message = null, isError = false)
    }

    fun install() {
        val url = _uiState.value.url.trim()
        if (url.isEmpty() || job?.isActive == true) return
        job = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInstalling = true, message = null, isError = false)
            runCatching { installer.install(url) }
                .onSuccess { pkg ->
                    _uiState.value = _uiState.value.copy(isInstalling = false, message = "已安装 ${pkg.manifest.id} ${pkg.manifest.version}")
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    _uiState.value = _uiState.value.copy(isInstalling = false, message = it.message, isError = true)
                }
        }
    }
}
