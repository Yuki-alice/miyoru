package app.miyoru.plugin.api

object PluginErrorCodes {
    const val TIMEOUT = "TIMEOUT"
    const val JS_ERROR = "JS_ERROR"
    const val LOAD_ERROR = "LOAD_ERROR"
    const val TOO_LARGE = "TOO_LARGE"
    const val INVALID_JSON = "INVALID_JSON"
    const val CAPABILITY_UNSUPPORTED = "CAPABILITY_UNSUPPORTED"
    const val NOT_IMPLEMENTED = "NOT_IMPLEMENTED"
}

class PluginException(val code: String, message: String, cause: Throwable? = null) : Exception(message, cause)
