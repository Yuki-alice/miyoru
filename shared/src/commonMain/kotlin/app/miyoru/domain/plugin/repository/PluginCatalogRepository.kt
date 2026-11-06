package app.miyoru.domain.plugin.repository

import app.miyoru.domain.plugin.model.CatalogEntry
import app.miyoru.domain.plugin.model.InstalledPlugin
import app.miyoru.domain.plugin.model.PluginCatalog
import app.miyoru.domain.plugin.model.PluginRepo
import app.miyoru.domain.plugin.model.PluginUpdate
import app.miyoru.domain.plugin.model.RefreshOutcome
import app.miyoru.domain.plugin.model.RepositoryPreview
import kotlinx.coroutines.flow.Flow

/**
 * 插件仓库目录：添加/识别/信任仓库，在仓库内按需安装与更新插件。
 *
 * 与 Mihon / Komikku / Miru 的操作模型一致：先添加仓库索引链接并确认信任，
 * 再按仓库浏览插件，逐个安装。**没有**单个插件包 URL 或本地 ZIP 的直装入口。
 */
interface PluginCatalogRepository {
    fun observeRepositories(): Flow<List<PluginRepo>>

    /** 索引缓存 + 已安装插件 + 仓库列表的组合视图；断网时仍可用最后一次已验证的索引。 */
    fun observeCatalog(): Flow<PluginCatalog>

    /** 拉取并识别仓库，不落库；结果按身份指纹暂存在 Data 层内存。 */
    suspend fun inspectRepository(url: String): RepositoryPreview

    /** 用户对识别结果点「信任并添加」后落库；同一 repositoryId 再次添加视为重新确认身份指纹。 */
    suspend fun addRepository(fingerprint: String): PluginRepo

    /**
     * 删除仓库。`removePlugins = true` 时同时卸载该仓库下的插件，
     * 但收藏/历史/进度等用户数据一律不级联删除。
     */
    suspend fun removeRepository(id: String, removePlugins: Boolean)

    suspend fun setRepositoryEnabled(id: String, enabled: Boolean)

    /** 刷新单个仓库；失败保留旧索引缓存，身份指纹变化时要求重新确认而不静默替换。 */
    suspend fun refreshRepository(id: String): RefreshOutcome

    suspend fun refreshAll(): RefreshOutcome

    /** 按索引条目下载 ZIP、比对整包 SHA-256、安装并绑定来源仓库。 */
    suspend fun install(repositoryId: String, pluginId: String): InstalledPlugin

    /** 只能从插件的来源仓库更新；跨仓库覆盖会被拒绝。 */
    suspend fun update(pluginId: String): InstalledPlugin

    suspend fun checkForUpdates(): List<PluginUpdate>

    /** 安装前的权限/风险确认信息。 */
    suspend fun catalogEntry(repositoryId: String, pluginId: String): CatalogEntry?
}
