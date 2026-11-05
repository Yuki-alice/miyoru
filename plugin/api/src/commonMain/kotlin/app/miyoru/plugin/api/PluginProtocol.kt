package app.miyoru.plugin.api

import kotlinx.serialization.Serializable

@Serializable
data class PluginCall(val capability: String, val payload: Map<String, String> = emptyMap())

@Serializable
data class PluginResult(val ok: Boolean, val json: String = "{}", val errorCode: String? = null)
