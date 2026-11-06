package app.miyoru.feature.extensions.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.domain.plugin.model.CatalogEntry
import app.miyoru.domain.plugin.model.PluginCatalog
import app.miyoru.domain.plugin.repository.PluginCatalogRepository
import app.miyoru.domain.plugin.repository.PluginRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val TAB_AVAILABLE = 0
const val TAB_UPDATES = 1
const val TAB_INSTALLED = 2

data class ExtensionsUiState(
    val catalog: PluginCatalog = PluginCatalog(),
    val tab: Int = TAB_AVAILABLE,
    val isRefreshing: Boolean = false,
    val busy: Set<String> = emptySet(),
    /** 安装前的权限/风险确认；用户确认后才真正下载与安装。 */
    val pendingInstall: CatalogEntry? = null,
    val message: String? = null,
    val isError: Boolean = false,
)

/**
 * 插件商店：按仓库分组浏览插件目录，三个分面（可安装 / 有更新 / 已安装），
 * 逐个按需安装。没有单包 URL 输入，所有插件都来自用户已信任的仓库。
 */
class ExtensionsViewModel(
    private val catalog: PluginCatalogRepository,
    private val plugins: PluginRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            catalog.observeCatalog()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(message = e.message, isError = true)
                }
                .collect { snapshot ->
                    _uiState.update { it.copy(catalog = snapshot) }
                }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(tab = tab, message = null) }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, message = null) }
            runCatching { catalog.refreshAll() }
                .onSuccess { outcome ->
                    val notes = mutableListOf<String>()
                    if (outcome.refreshed > 0) notes += "已刷新 ${outcome.refreshed} 个仓库"
                    if (outcome.updateCount > 0) notes += "发现 ${outcome.updateCount} 个更新"
                    if (outcome.fingerprintChanged.isNotEmpty()) {
                        notes += "有仓库身份指纹变化（ID／链接／发布者公钥变更），请到插件仓库页重新确认信任"
                    }
                    if (outcome.failed.isNotEmpty()) {
                        notes += "${outcome.failed.size} 个仓库刷新失败（已保留上次索引）"
                    }
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            message = notes.ifEmpty { listOf("尚未添加插件仓库") }.joinToString("；"),
                            isError = outcome.failed.isNotEmpty() || outcome.fingerprintChanged.isNotEmpty(),
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(isRefreshing = false, message = friendly(e), isError = true)
                    }
                }
        }
    }

    fun requestInstall(entry: CatalogEntry) {
        _uiState.update { it.copy(pendingInstall = entry, message = null) }
    }

    fun dismissInstall() {
        _uiState.update { it.copy(pendingInstall = null) }
    }

    fun confirmInstall() = withBusy("install") {
        val entry = _uiState.value.pendingInstall ?: return@withBusy
        catalog.install(entry.repositoryId, entry.pluginId)
        _uiState.update {
            it.copy(
                pendingInstall = null,
                message = "已安装 ${entry.name} ${entry.version}，可在数据源页启用",
                isError = false,
            )
        }
    }

    fun update(pluginId: String) = withBusy(pluginId) {
        val installed = catalog.update(pluginId)
        _uiState.update {
            it.copy(message = "已更新 ${installed.id} 到 ${installed.version}", isError = false)
        }
    }

    fun uninstall(pluginId: String) = withBusy(pluginId) {
        plugins.uninstall(pluginId)
        _uiState.update { it.copy(message = "已卸载 $pluginId（收藏与历史保留）", isError = false) }
    }

    fun rollback(pluginId: String) = withBusy(pluginId) {
        val rolled = plugins.rollback(pluginId)
        _uiState.update { it.copy(message = "已回滚 ${rolled.id} 到 ${rolled.version}", isError = false) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    private fun withBusy(key: String, block: suspend () -> Unit) {
        if (key in _uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = it.busy + key, message = null) }
            runCatching { block() }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(message = friendly(e), isError = true) }
                }
            _uiState.update { it.copy(busy = it.busy - key) }
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is app.miyoru.core.common.AppError.PluginSourceMismatch ->
            "该插件已由另一个仓库安装，请先卸载再安装"
        is app.miyoru.core.common.AppError.PluginIncompatible -> e.reason
        is app.miyoru.core.common.AppError.RepositoryInvalid -> "仓库索引不合法：${e.detail}"
        is app.miyoru.core.common.AppError.InvalidInput -> e.detail
        is app.miyoru.core.common.AppError.Network -> "网络请求失败"
        else -> e.message ?: "操作失败"
    }
}
