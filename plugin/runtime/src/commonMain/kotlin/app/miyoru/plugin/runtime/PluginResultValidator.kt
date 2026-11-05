package app.miyoru.plugin.runtime

import app.miyoru.plugin.api.PluginResult

object PluginResultValidator {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun validate(result: PluginResult): Boolean =
        result.json.encodeToByteArray().size <= MAX_BYTES
}
