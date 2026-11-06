package app.miyoru.domain.plugin.model

import kotlinx.serialization.Serializable

/**
 * 已安装插件。`repositoryId` 是必填的来源仓库；空串只可能来自 v1 → v2 迁移前的历史安装，
 * 这类插件不能通过仓库更新，只能先卸载再从仓库重新安装。
 */
@Serializable
data class InstalledPlugin(
    val id: String,
    val version: String,
    val enabled: Boolean,
    val repositoryId: String = "",
)

@Serializable
data class PluginUpdate(
    val pluginId: String,
    val latestVersion: String,
    val repositoryId: String = "",
)
