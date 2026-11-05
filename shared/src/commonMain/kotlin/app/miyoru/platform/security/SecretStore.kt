package app.miyoru.platform.security

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 密钥/Token/Cookie 安全存储。SQLite/日志不得存明文凭据（目标文档 §7.3/§9）。
 * 平台 actual（Keystore/Keychain/桌面安全存储）在平台集成轮替换 InMemory 绑定。
 */
interface SecretStore {
    suspend fun put(alias: String, value: ByteArray)
    suspend fun get(alias: String): ByteArray?
    suspend fun delete(alias: String)
}

class InMemorySecretStore : SecretStore {
    private val map = mutableMapOf<String, ByteArray>()
    private val mutex = Mutex()
    override suspend fun put(alias: String, value: ByteArray) {
        mutex.withLock { map[alias] = value.copyOf() }
    }
    override suspend fun get(alias: String): ByteArray? =
        mutex.withLock { map[alias]?.copyOf() }
    override suspend fun delete(alias: String) {
        mutex.withLock { map.remove(alias) }
    }
}
