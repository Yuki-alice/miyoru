package app.miyoru.core.common

interface Logger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String, cause: Throwable? = null)
    fun e(tag: String, message: String, cause: Throwable? = null)
}
