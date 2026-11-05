package app.miyoru.domain.plugin.model

import kotlinx.serialization.Serializable

@Serializable
data class InstalledPlugin(
    val id: String,
    val version: String,
    val enabled: Boolean,
)

@Serializable
data class PluginUpdate(val pluginId: String, val latestVersion: String)
