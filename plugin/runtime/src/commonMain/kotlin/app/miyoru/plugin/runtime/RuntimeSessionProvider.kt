package app.miyoru.plugin.runtime

import app.miyoru.plugin.api.PluginErrorCodes
import app.miyoru.plugin.api.PluginException
import app.miyoru.plugin.api.PluginSession
import app.miyoru.plugin.api.PluginSessionProvider
import app.miyoru.plugin.runtime.install.PluginFileSystem
import app.miyoru.plugin.runtime.install.PluginLayout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * plugin:runtime 侧的会话提供实现：从安装目录读 bundle.js，用自有引擎建会话。
 * 引擎实例按插件缓存；release 时关闭。
 */
class RuntimeSessionProvider(
    private val fs: PluginFileSystem,
    private val config: PluginEngineConfig = PluginEngineConfig(),
) : PluginSessionProvider {
    private val engines = mutableMapOf<String, QuickJsPluginEngine>()
    private val lock = Mutex()

    override suspend fun session(pluginId: String): PluginSession {
        val engine = lock.withLock { engines.getOrPut(pluginId) { QuickJsPluginEngine(config) } }
        return try {
            val bundle = fs.readFile("${PluginLayout.pluginDir(pluginId)}/bundle.js")
            engine.createSession(pluginId, bundle.decodeToString())
        } catch (e: Exception) {
            if (e is PluginException) throw e
            throw PluginException(PluginErrorCodes.LOAD_ERROR, "open $pluginId failed: ${e.message}", e)
        }
    }

    override suspend fun release(pluginId: String) {
        val engine = lock.withLock { engines.remove(pluginId) }
        engine?.close(pluginId)
    }
}
