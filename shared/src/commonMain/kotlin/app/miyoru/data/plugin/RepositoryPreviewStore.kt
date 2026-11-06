package app.miyoru.data.plugin

import app.miyoru.plugin.api.RepositoryIndex
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PendingRepository(
    val url: String,
    val index: RepositoryIndex,
    val fingerprint: String,
)

/**
 * 识别结果暂存：用户点「信任并添加」之前，仓库绝不落库。
 * 只存在进程内存，重启后失效，需要重新识别；避免 Domain 依赖 plugin:api 类型，
 * 也避免添加仓库时二次拉取索引。
 */
class RepositoryPreviewStore {
    private val mutex = Mutex()
    private val pending = mutableMapOf<String, PendingRepository>()

    suspend fun put(entry: PendingRepository) {
        mutex.withLock { pending[entry.fingerprint] = entry }
    }

    suspend fun take(fingerprint: String): PendingRepository? =
        mutex.withLock { pending.remove(fingerprint) }

    suspend fun clear() {
        mutex.withLock { pending.clear() }
    }
}
