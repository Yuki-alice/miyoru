package app.miyoru.feature.repositories.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.miyoru.domain.plugin.model.PluginRepo
import app.miyoru.domain.plugin.model.RepositoryPreview
import app.miyoru.domain.plugin.repository.PluginCatalogRepository
import app.miyoru.domain.plugin.repository.PluginRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RepositoriesUiState(
    val isLoading: Boolean = true,
    val repositories: List<PluginRepo> = emptyList(),
    val isRefreshing: Boolean = false,
    val showAddDialog: Boolean = false,
    val addUrl: String = "",
    val isInspecting: Boolean = false,
    val preview: RepositoryPreview? = null,
    val dialogError: String? = null,
    val pendingDelete: PluginRepo? = null,
    val deletePluginCount: Int = 0,
    val installedByRepo: Map<String, Int> = emptyMap(),
    val message: String? = null,
    val isError: Boolean = false,
)

/**
 * 插件仓库页：添加仓库链接 → 系统识别（不落库）→ 用户确认信任 → 落库。
 * 对齐 Mihon / Komikku / Miru 的「扩展仓库」交互，且删除仓库必须提示影响范围。
 */
class RepositoriesViewModel(
    private val catalog: PluginCatalogRepository,
    private val plugins: PluginRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RepositoriesUiState())
    val uiState: StateFlow<RepositoriesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            catalog.observeRepositories()
                .catch { e ->
                    _uiState.update {
                        it.copy(isLoading = false, message = e.message, isError = true)
                    }
                }
                .collect { repos ->
                    _uiState.update { it.copy(isLoading = false, repositories = repos) }
                }
        }
        viewModelScope.launch {
            plugins.observeInstalled()
                .catch { /* 已安装列表不可用时不影响仓库管理主流程 */ }
                .collect { list ->
                    _uiState.update {
                        it.copy(installedByRepo = list.groupBy { p -> p.repositoryId }.mapValues { e -> e.value.size })
                    }
                }
        }
    }

    fun showAddDialog() {
        _uiState.update {
            it.copy(
                showAddDialog = true, addUrl = "", preview = null,
                dialogError = null, message = null, isError = false,
            )
        }
    }

    fun dismissAddDialog() {
        _uiState.update {
            it.copy(showAddDialog = false, preview = null, dialogError = null, isInspecting = false)
        }
    }

    fun updateUrl(url: String) {
        _uiState.update { it.copy(addUrl = url, preview = null, dialogError = null) }
    }

    /** 识别仓库：拉取并校验索引，展示结果，但不落库。 */
    fun inspect() {
        val url = _uiState.value.addUrl.trim()
        if (url.isBlank() || _uiState.value.isInspecting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isInspecting = true, preview = null, dialogError = null) }
            runCatching { catalog.inspectRepository(url) }
                .onSuccess { preview ->
                    _uiState.update { it.copy(isInspecting = false, preview = preview) }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(isInspecting = false, dialogError = friendly(e))
                    }
                }
        }
    }

    /** 用户确认信任后才写入仓库与索引缓存。 */
    fun confirmAdd() {
        val fingerprint = _uiState.value.preview?.fingerprint ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isInspecting = true, dialogError = null) }
            runCatching { catalog.addRepository(fingerprint) }
                .onSuccess { repo ->
                    _uiState.update {
                        it.copy(
                            isInspecting = false, showAddDialog = false, preview = null,
                            message = "已信任并添加仓库「${repo.name}」，可在插件商店按需安装",
                            isError = false,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(isInspecting = false, dialogError = friendly(e)) }
                }
        }
    }

    fun refresh(id: String) = refreshAll(listOf(id))

    fun refreshAll() = refreshAll(null)

    private fun refreshAll(ids: List<String>?) {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, message = null) }
            runCatching {
                when {
                    ids == null -> catalog.refreshAll()
                    ids.size == 1 -> catalog.refreshRepository(ids.single())
                    else -> {
                        var total = 0
                        ids.forEach { total += catalog.refreshRepository(it).refreshed }
                        app.miyoru.domain.plugin.model.RefreshOutcome(refreshed = total)
                    }
                }
            }
                .onSuccess { outcome ->
                    val notes = mutableListOf<String>()
                    if (outcome.refreshed > 0) notes += "已刷新 ${outcome.refreshed} 个仓库"
                    if (outcome.fingerprintChanged.isNotEmpty()) {
                        notes += "有 ${outcome.fingerprintChanged.size} 个仓库身份指纹变化（ID／链接／发布者公钥变更），请重新识别并确认信任"
                    }
                    if (outcome.failed.isNotEmpty()) notes += "${outcome.failed.size} 个仓库刷新失败：${outcome.failed.first()}"
                    if (outcome.updateCount > 0) notes += "发现 ${outcome.updateCount} 个插件更新"
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            message = notes.ifEmpty { listOf("没有需要刷新的仓库") }.joinToString("；"),
                            isError = outcome.failed.isNotEmpty(),
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

    fun setEnabled(repo: PluginRepo, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { catalog.setRepositoryEnabled(repo.id, enabled) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(message = friendly(e), isError = true) }
                }
        }
    }

    /** 删除前展示影响范围：该仓库下已安装的插件数由 observeInstalled 计算。 */
    fun requestDelete(repo: PluginRepo) {
        val count = _uiState.value.installedByRepo[repo.id] ?: 0
        _uiState.update { it.copy(pendingDelete = repo, deletePluginCount = count) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(pendingDelete = null, deletePluginCount = 0) }
    }

    /** removePlugins=true 时同时卸载插件；收藏/历史/进度不受影响。 */
    fun confirmDelete(removePlugins: Boolean) {
        val repo = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            runCatching { catalog.removeRepository(repo.id, removePlugins) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            pendingDelete = null, deletePluginCount = 0,
                            message = if (removePlugins) {
                                "已移除仓库「${repo.name}」并卸载其插件（收藏与历史保留）"
                            } else {
                                "已移除仓库「${repo.name}」，已安装插件保留但不可更新"
                            },
                            isError = false,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(pendingDelete = null, message = friendly(e), isError = true) }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is app.miyoru.core.common.AppError.RepositoryInvalid -> "仓库索引不合法：${e.detail}"
        is app.miyoru.core.common.AppError.RepositoryUntrusted -> "仓库尚未确认信任，请重新识别"
        is app.miyoru.core.common.AppError.RepositoryFingerprintChanged -> "仓库身份指纹变化，请重新识别并确认信任"
        is app.miyoru.core.common.AppError.InvalidInput -> e.detail
        is app.miyoru.core.common.AppError.Network -> "网络请求失败，请检查仓库链接"
        else -> e.message ?: "操作失败"
    }
}
