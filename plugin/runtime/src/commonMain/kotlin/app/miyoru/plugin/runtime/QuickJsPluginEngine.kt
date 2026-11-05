package app.miyoru.plugin.runtime

import app.miyoru.plugin.api.PluginCall
import app.miyoru.plugin.api.PluginErrorCodes
import app.miyoru.plugin.api.PluginException
import app.miyoru.plugin.api.PluginEngine
import app.miyoru.plugin.api.PluginResult
import app.miyoru.plugin.api.PluginSession
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class PluginEngineConfig(
    val timeoutMs: Long = 10_000,
    val maxResultBytes: Int = 2 * 1024 * 1024,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
)

/**
 * 每插件独立 QuickJs 实例 + 串行调度（同一插件调用串行，不同插件可有限并发）。
 * 单次执行有原生超时；超时/取消映射为 TIMEOUT；输出限大小 + 必须是合法 JSON。
 */
class QuickJsPluginEngine(
    private val config: PluginEngineConfig = PluginEngineConfig(),
) : PluginEngine {
    private val sessions = mutableMapOf<String, QuickJsPluginSession>()
    private val lock = Mutex()

    override suspend fun createSession(pluginId: String, entrySource: String): PluginSession {
        lock.withLock { sessions[pluginId] }?.let { return it }
        val qjs = QuickJs.create(config.dispatcher)
        qjs.evaluationTimeoutMillis = config.timeoutMs
        try {
            // 忽略 bundle 产物值：尾部追加字符串字面量，使 completion 恒为 String，避免 JsObject→Unit 无转换器。
            qjs.evaluate<String>(entrySource + "\n'__miyoru_loaded__'", "bundle.js")
        } catch (e: Exception) {
            try {
                qjs.close()
            } catch (_: Exception) {
            }
            if (e is QuickJsInterruptedException) throw PluginException(PluginErrorCodes.TIMEOUT, "bundle timed out", e)
            throw PluginException(PluginErrorCodes.LOAD_ERROR, "bundle failed: ${e.message}", e)
        }
        val session = QuickJsPluginSession(pluginId, qjs, config)
        lock.withLock { sessions[pluginId] = session }
        return session
    }

    override suspend fun close(pluginId: String) {
        lock.withLock { sessions.remove(pluginId) }?.close()
    }

    internal class QuickJsPluginSession(
        val pluginId: String,
        private val qjs: QuickJs,
        private val config: PluginEngineConfig,
    ) : PluginSession {
        private val mutex = Mutex()

        override suspend fun invoke(call: PluginCall): PluginResult = mutex.withLock {
            try {
                val capLiteral = Json.encodeToString(call.capability)
                val isFunc = qjs.evaluate<Boolean>("typeof globalThis[$capLiteral] === 'function'", "cap.js")
                if (isFunc != true) return PluginResult(ok = false, errorCode = PluginErrorCodes.CAPABILITY_UNSUPPORTED)
                val payloadLiteral = Json.encodeToString(call.payload)
                val raw = qjs.evaluate<String>("JSON.stringify(globalThis[$capLiteral]($payloadLiteral))", "invoke.js")
                if (raw.encodeToByteArray().size > config.maxResultBytes) {
                    return PluginResult(ok = false, errorCode = PluginErrorCodes.TOO_LARGE)
                }
                try {
                    Json.parseToJsonElement(raw)
                } catch (e: Exception) {
                    return PluginResult(ok = false, errorCode = PluginErrorCodes.INVALID_JSON)
                }
                PluginResult(ok = true, json = raw)
            } catch (e: QuickJsInterruptedException) {
                PluginResult(ok = false, errorCode = PluginErrorCodes.TIMEOUT)
            } catch (e: QuickJsException) {
                PluginResult(ok = false, errorCode = PluginErrorCodes.JS_ERROR)
            }
        }

        override suspend fun close() {
            try {
                qjs.close()
            } catch (_: Exception) {
            }
        }
    }
}
