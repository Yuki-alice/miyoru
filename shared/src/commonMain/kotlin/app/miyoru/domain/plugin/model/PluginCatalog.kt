package app.miyoru.domain.plugin.model

import kotlinx.serialization.Serializable

/** 按仓库分组的插件目录快照，供「插件商店」页三分面展示。 */
@Serializable
data class PluginCatalog(
    val groups: List<RepositoryGroup> = emptyList(),
    val updates: List<PluginUpdate> = emptyList(),
    /** 来源仓库已被移除的已安装插件：仍可用，但不能检查更新或更新。 */
    val orphans: List<InstalledPlugin> = emptyList(),
)

@Serializable
data class RepositoryGroup(
    val repository: PluginRepo,
    val entries: List<CatalogEntry> = emptyList(),
)

@Serializable
data class CatalogEntry(
    val pluginId: String,
    val repositoryId: String,
    val name: String,
    val version: String,
    val mediaTypes: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
    /** 申请的网络域名，安装前必须向用户展示。 */
    val domains: List<String> = emptyList(),
    val description: String? = null,
    val installedVersion: String? = null,
    val state: CatalogEntryState = CatalogEntryState.AVAILABLE,
    val reason: String? = null,
)

/** 一次刷新（单仓库或全部）的汇总，不因单个仓库失败而整体失败。 */
@Serializable
data class RefreshOutcome(
    val refreshed: Int = 0,
    val failed: List<String> = emptyList(),
    /** 身份指纹与已信任值不同的仓库 id：索引已保留旧缓存，需用户重新识别并确认信任。 */
    val fingerprintChanged: List<String> = emptyList(),
    val updateCount: Int = 0,
)
