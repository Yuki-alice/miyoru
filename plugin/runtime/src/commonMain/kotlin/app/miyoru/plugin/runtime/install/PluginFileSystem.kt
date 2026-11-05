package app.miyoru.plugin.runtime.install

/**
 * 插件包文件操作抽象：所有路径均为相对安装根目录的 `/` 分隔相对路径，不存平台绝对路径。
 * 实现基于 kotlinx-io SystemFileSystem，全端共用。
 */
interface PluginFileSystem {
    suspend fun createDir(path: String)
    suspend fun writeFile(path: String, bytes: ByteArray)
    suspend fun readFile(path: String): ByteArray
    suspend fun listFiles(dir: String): List<String>
    suspend fun exists(path: String): Boolean
    suspend fun move(from: String, to: String)
    suspend fun delete(path: String)
}
