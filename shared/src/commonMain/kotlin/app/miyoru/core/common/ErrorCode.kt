package app.miyoru.core.common

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.io.IOException

/** 稳定错误码：日志与插件开发者定位用，不向用户展示原始堆栈。 */
object ErrorCode {
    const val NETWORK = "E_NETWORK"
    const val TIMEOUT = "E_TIMEOUT"
    const val PARSE = "E_PARSE"
    const val AUTH = "E_AUTH"
    const val RATE_LIMITED = "E_RATE_LIMITED"
    const val CAPABILITY_UNSUPPORTED = "E_CAPABILITY_UNSUPPORTED"
    const val PLUGIN_INCOMPATIBLE = "E_PLUGIN_INCOMPATIBLE"
    const val STORAGE = "E_STORAGE"
    const val INVALID_INPUT = "E_INVALID_INPUT"
    const val PERMISSION_DENIED = "E_PERMISSION_DENIED"
    const val REPOSITORY_INVALID = "E_REPOSITORY_INVALID"
    const val REPOSITORY_UNTRUSTED = "E_REPOSITORY_UNTRUSTED"
    const val REPOSITORY_FINGERPRINT_CHANGED = "E_REPOSITORY_FINGERPRINT_CHANGED"
    const val PLUGIN_SOURCE_MISMATCH = "E_PLUGIN_SOURCE_MISMATCH"
}

val AppError.code: String
    get() = when (this) {
        is AppError.Network -> ErrorCode.NETWORK
        is AppError.Timeout -> ErrorCode.TIMEOUT
        is AppError.Parse -> ErrorCode.PARSE
        is AppError.Authentication -> ErrorCode.AUTH
        is AppError.RateLimited -> ErrorCode.RATE_LIMITED
        is AppError.CapabilityUnsupported -> ErrorCode.CAPABILITY_UNSUPPORTED
        is AppError.PluginIncompatible -> ErrorCode.PLUGIN_INCOMPATIBLE
        is AppError.Storage -> ErrorCode.STORAGE
        is AppError.InvalidInput -> ErrorCode.INVALID_INPUT
        AppError.PermissionDenied -> ErrorCode.PERMISSION_DENIED
        is AppError.RepositoryInvalid -> ErrorCode.REPOSITORY_INVALID
        is AppError.RepositoryUntrusted -> ErrorCode.REPOSITORY_UNTRUSTED
        is AppError.RepositoryFingerprintChanged -> ErrorCode.REPOSITORY_FINGERPRINT_CHANGED
        is AppError.PluginSourceMismatch -> ErrorCode.PLUGIN_SOURCE_MISMATCH
    }

/**
 * 非取消异常 → AppError。CancellationException（含协程取消）一律重新抛出，
 * 只有 TimeoutCancellationException（withTimeout）转为 [AppError.Timeout]。
 */
fun Throwable.toAppError(operation: String): AppError {
    if (this is AppError) return this
    if (this is TimeoutCancellationException) return AppError.Timeout(operation)
    if (this is kotlinx.coroutines.CancellationException) throw this
    if (this is IOException) return AppError.Network(this)
    if (this is IllegalArgumentException) return AppError.InvalidInput(this.message ?: operation)
    return AppError.Parse("unexpected ${this::class.simpleName}: ${this.message}")
}
