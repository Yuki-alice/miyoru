package app.miyoru.plugin.api

import kotlinx.serialization.Serializable

const val CURRENT_API_VERSION = 1

val PLUGIN_MEDIA_TYPES = setOf("video", "novel", "comic")
val PLUGIN_CAPABILITIES = setOf("home", "search", "detail", "chapters", "pages", "streams", "filters")

@Serializable
data class PluginPermissions(
    val domains: List<String> = emptyList(),
)

@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int,
    val minAppVersion: String,
    val author: String = "",
    val homepage: String? = null,
    val mediaTypes: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val permissions: PluginPermissions = PluginPermissions(),
    val entry: String = "bundle.js",
    val entrySha256: String? = null,
    val signature: String? = null,
)
