package app.miyoru.plugin.runtime.install

class FakeFs : PluginFileSystem {
    val files = mutableMapOf<String, ByteArray>()
    val dirs = mutableSetOf<String>()

    private fun norm(path: String): String {
        require(path.isNotBlank() && !path.startsWith("/")) { "bad path: $path" }
        val segs = path.split("/").filter { it.isNotEmpty() }
        require(segs.none { it == "." || it == ".." } && !path.contains("\\")) { "bad path: $path" }
        return segs.joinToString("/")
    }

    override suspend fun createDir(path: String) { dirs.add(norm(path)) }
    override suspend fun writeFile(path: String, bytes: ByteArray) { files[norm(path)] = bytes.copyOf() }
    override suspend fun readFile(path: String): ByteArray =
        files[norm(path)]?.copyOf() ?: throw IllegalArgumentException("not found: $path")
    override suspend fun listFiles(dir: String): List<String> {
        val prefix = norm(dir) + "/"
        return files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore("/") }
    }
    override suspend fun exists(path: String): Boolean {
        val n = norm(path)
        return files.containsKey(n) || dirs.contains(n) || files.keys.any { it.startsWith("$n/") }
    }
    override suspend fun move(from: String, to: String) {
        val f = norm(from); val t = norm(to)
        val moved = files.keys.filter { it == f || it.startsWith("$f/") }
        if (moved.isEmpty() && f !in dirs) throw IllegalArgumentException("not found: $from")
        moved.forEach { files[t + it.removePrefix(f)] = files.remove(it)!! }
        dirs.remove(f); dirs.add(t)
    }
    override suspend fun delete(path: String) {
        val n = norm(path)
        files.keys.filter { it == n || it.startsWith("$n/") }.forEach { files.remove(it) }
        dirs.remove(n)
    }
}
