package app.miyoru.plugin.runtime.install
import app.miyoru.plugin.api.PackageEntry

import app.miyoru.plugin.api.PluginManifest

/**
 * 安装流程：暂存区写入 → 校验 → 旧版搬备份 → 暂存搬正 → 清理。
 * 失败保留旧版（DB 的 previous_* 由 PluginRepositoryImpl 在成功后更新）。
 */
class PluginInstaller(
    private val fs: PluginFileSystem,
    private val validator: PluginPackageValidator,
) {
    data class InstallResult(val manifest: PluginManifest, val hadPrevious: Boolean)

    suspend fun install(entries: List<PackageEntry>): InstallResult {
        val validated = validator.validate(entries)
        val id = validated.manifest.id
        val staging = PluginLayout.stagingDir(id)
        val current = PluginLayout.pluginDir(id)
        val backup = PluginLayout.backupDir(id)
        // 清理残留暂存/备份，保证幂等
        fs.delete(staging)
        fs.delete(backup)
        try {
            validated.entries.forEach { e ->
                if (!e.path.endsWith("/")) fs.writeFile("$staging/${e.path}", e.bytes)
            }
            val hadPrevious = fs.exists(current)
            if (hadPrevious) fs.move(current, backup)
            try {
                fs.move(staging, current)
            } catch (e: Exception) {
                // 新版就位失败：尝试恢复旧版
                if (hadPrevious && fs.exists(backup) && !fs.exists(current)) {
                    fs.move(backup, current)
                }
                throw e
            }
            fs.delete(staging)
            return InstallResult(validated.manifest, hadPrevious)
        } catch (e: Exception) {
            fs.delete(staging)
            if (e is PackageException) throw e
            throw PackageException(PackageErrorCodes.BAD_MANIFEST, "install failed: ${e.message}", e)
        }
    }

    /** 回滚：删当前版，备份搬回。 */
    suspend fun rollback(pluginId: String) {
        val current = PluginLayout.pluginDir(pluginId)
        val backup = PluginLayout.backupDir(pluginId)
        if (!fs.exists(backup)) throw PackageException(PackageErrorCodes.MISSING_ENTRY, "no backup for $pluginId")
        fs.delete(current)
        fs.move(backup, current)
    }

    suspend fun uninstall(pluginId: String) {
        fs.delete(PluginLayout.pluginDir(pluginId))
        fs.delete(PluginLayout.backupDir(pluginId))
        fs.delete(PluginLayout.stagingDir(pluginId))
    }
}
