package app.miyoru.data.source

import app.miyoru.core.common.AppError
import app.miyoru.core.common.toAppError
import app.miyoru.plugin.api.PluginCall
import app.miyoru.plugin.api.PluginResult
import app.miyoru.plugin.api.PluginSessionProvider
import app.miyoru.plugin.api.PluginErrorCodes
import app.miyoru.plugin.api.PluginException

fun PluginResult.requireOk(operation: String): String {
    if (ok) return json
    throw when (errorCode) {
        PluginErrorCodes.TIMEOUT -> AppError.Timeout(operation)
        PluginErrorCodes.CAPABILITY_UNSUPPORTED -> AppError.CapabilityUnsupported(operation)
        else -> AppError.Parse("$operation failed: $errorCode")
    }
}

fun PluginException.toAppError(): AppError = when (code) {
    PluginErrorCodes.TIMEOUT -> AppError.Timeout(message ?: "plugin")
    PluginErrorCodes.CAPABILITY_UNSUPPORTED -> AppError.CapabilityUnsupported(message ?: "plugin")
    else -> AppError.Parse(message ?: "plugin error: $code")
}

suspend fun PluginSessionProvider.invokeCall(
    pluginId: String,
    capability: String,
    payload: Map<String, String>,
    operation: String,
): String {
    val session = try {
        session(pluginId)
    } catch (e: PluginException) {
        throw e.toAppError()
    } catch (e: Exception) {
        throw e.toAppError(operation)
    }
    return try {
        session.invoke(PluginCall(capability, payload)).requireOk(operation)
    } catch (e: AppError) {
        throw e
    } catch (e: Exception) {
        throw e.toAppError(operation)
    }
}
