package app.miyoru.plugin.api

interface PluginEngine {
    suspend fun createSession(pluginId: String, entrySource: String): PluginSession
    suspend fun close(pluginId: String)
}

interface PluginSession {
    suspend fun invoke(call: PluginCall): PluginResult
    suspend fun close()
}

/**
 * 会话提供：shared/data 经此接口拿插件会话，不直接碰 bundle 文件与引擎实现。
 * 实现在 plugin:runtime（读安装目录 bundle + 自有引擎），由组合根装配。
 */
interface PluginSessionProvider {
    suspend fun session(pluginId: String): PluginSession
    suspend fun release(pluginId: String)
}
