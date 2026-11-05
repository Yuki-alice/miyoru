package app.miyoru.plugin.runtime.install

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write

class SystemPluginFileSystem(root: Path) : PluginFileSystem {
    private val root: Path = root
    private val mutex = Mutex()

    private fun resolve(path: String): Path {
        require(path.isNotBlank()) { "empty path" }
        require(!path.startsWith("/")) { "absolute path: $path" }
        val parts = path.split("/").filter { it.isNotEmpty() }
        require(parts.isNotEmpty() && parts.none { it == "." || it == ".." }) { "bad path: $path" }
        var p = root
        parts.forEach { p = Path(p, it) }
        return p
    }

    override suspend fun createDir(path: String) {
        mutex.withLock { SystemFileSystem.createDirectories(resolve(path)) }
    }

    override suspend fun writeFile(path: String, bytes: ByteArray) {
        mutex.withLock {
            val target = resolve(path)
            val parent = target.parent
            if (parent != null) SystemFileSystem.createDirectories(parent)
            SystemFileSystem.sink(target).buffered().use { it.write(bytes) }
        }
    }

    override suspend fun readFile(path: String): ByteArray {
        mutex.withLock { return SystemFileSystem.source(resolve(path)).buffered().use { it.readByteArray() } }
    }

    override suspend fun listFiles(dir: String): List<String> {
        mutex.withLock {
            val base = resolve(dir)
            if (!SystemFileSystem.exists(base)) return emptyList()
            return SystemFileSystem.list(base).map { it.name }
        }
    }

    override suspend fun exists(path: String): Boolean {
        mutex.withLock { return SystemFileSystem.exists(resolve(path)) }
    }

    override suspend fun move(from: String, to: String) {
        mutex.withLock {
            val src = resolve(from)
            val dst = resolve(to)
            val parent = dst.parent
            if (parent != null) SystemFileSystem.createDirectories(parent)
            SystemFileSystem.atomicMove(src, dst)
        }
    }

    override suspend fun delete(path: String) {
        mutex.withLock { deleteRecursive(resolve(path)) }
    }

    private fun deleteRecursive(p: Path) {
        val meta = SystemFileSystem.metadataOrNull(p) ?: return
        if (meta.isDirectory) {
            SystemFileSystem.list(p).forEach { deleteRecursive(it) }
        }
        SystemFileSystem.delete(p, mustExist = false)
    }
}
