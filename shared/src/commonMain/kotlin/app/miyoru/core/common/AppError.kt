package app.miyoru.core.common

sealed class AppError(message: String?, cause: Throwable? = null) : Exception(message, cause) {
    data class Network(override val cause: Throwable) : AppError("network failed", cause)
    data class Timeout(val operation: String) : AppError("timeout: $operation")
    data class Parse(val detail: String) : AppError("parse failed: $detail")
    data class Authentication(val sourceId: String) : AppError("source auth failed: $sourceId")
    data class RateLimited(val retryAfterSeconds: Long?) : AppError("rate limited")
    data class CapabilityUnsupported(val capability: String) : AppError("unsupported: $capability")
    data class PluginIncompatible(val pluginId: String, val reason: String) : AppError("incompatible: $pluginId")
    data class Storage(override val cause: Throwable) : AppError("storage failed", cause)
    data class InvalidInput(val detail: String) : AppError("invalid: $detail")
    data object PermissionDenied : AppError("permission denied")
}
