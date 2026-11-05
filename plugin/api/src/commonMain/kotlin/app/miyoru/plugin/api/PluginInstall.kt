package app.miyoru.plugin.api

import kotlinx.serialization.Serializable

/** 安装包来源：用户选择的 URL 下载字节或本地文件字节（平台侧读文件）。 */
@Serializable
data class PluginPackageSource(val fileName: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is PluginPackageSource && fileName == other.fileName && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = 31 * fileName.hashCode() + bytes.contentHashCode()
}

@Serializable
data class InstalledPackage(
    val manifest: PluginManifest,
    val packageSha256: String,
    val hadPrevious: Boolean,
)

/**
 * 安装门面（plugin:runtime 实现）：暂存→校验→原子替换→回滚。
 * shared/data 只依赖此接口，不碰文件系统与引擎实现。
 */
interface PluginInstallerFacade {
    suspend fun install(pkg: PluginPackageSource, enforceSignature: Boolean): InstalledPackage
    suspend fun rollback(pluginId: String)
    suspend fun uninstall(pluginId: String)
    /** 安装根目录下的相对路径（存 DB，不存绝对路径）。 */
    fun installDir(pluginId: String): String
}
