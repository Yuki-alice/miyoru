@file:OptIn(kotlin.time.ExperimentalTime::class)
package app.miyoru.data.plugin


import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.miyoru.core.common.AppError
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.plugin.model.InstalledPlugin
import app.miyoru.domain.plugin.model.PluginUpdate
import app.miyoru.domain.plugin.repository.PluginRepository
import app.miyoru.plugin.api.PluginInstallerFacade
import app.miyoru.plugin.api.PluginPackageSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private val jsonStrict = Json { ignoreUnknownKeys = true }

/**
 * 插件安装事务：文件系统先行（facade），成功后再提交数据库终态；
 * 崩溃恢复以 DB 状态 + 暂存/备份目录对账（结构文档 §7.3）。
 */
class PluginRepositoryImpl(
    private val db: MiyoruDatabase,
    private val installer: PluginInstallerFacade,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : PluginRepository {

    /** 安装包字节入口（供平台文件选择/下载调用）。 */
    suspend fun install(pkg: PluginPackageSource, enforceSignature: Boolean): InstalledPlugin =
        withContext(io) {
            val installed = installer.install(pkg, enforceSignature)
            val m = installed.manifest
            val now = Clock.System.now().toEpochMilliseconds()
            val existing = db.pluginQueries.selectById(m.id).executeAsOneOrNull()
            db.transaction {
                db.pluginQueries.insertOrReplace(
                    id = m.id,
                    name = m.name,
                    version = m.version,
                    api_version = m.apiVersion.toLong(),
                    media_types_json = jsonStrict.encodeToString(m.mediaTypes),
                    capabilities_json = jsonStrict.encodeToString(m.capabilities),
                    manifest_json = jsonStrict.encodeToString(m),
                    package_sha256 = installed.packageSha256,
                    signature = m.signature,
                    repository_id = existing?.repository_id,
                    relative_install_dir = installer.installDir(m.id),
                    previous_version = existing?.version,
                    previous_package_sha256 = existing?.package_sha256,
                    previous_relative_install_dir = existing?.relative_install_dir,
                    previous_manifest_json = existing?.manifest_json,
                    enabled = 1,
                    last_error = null,
                    installed_at = existing?.installed_at ?: now,
                    updated_at = now,
                )
            }
            InstalledPlugin(m.id, m.version, true)
        }

    override fun observeInstalled(): Flow<List<InstalledPlugin>> =
        db.pluginQueries.selectAll().asFlow().mapToList(io).map { rows ->
            rows.map { InstalledPlugin(it.id, it.version, it.enabled == 1L) }
        }

    override suspend fun setEnabled(pluginId: String, enabled: Boolean): Unit = withContext(io) {
        db.pluginQueries.selectById(pluginId).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown plugin: $pluginId")
        db.pluginQueries.updateEnabled(if (enabled) 1L else 0L, Clock.System.now().toEpochMilliseconds(), pluginId)
    }

    override suspend fun checkForUpdate(pluginId: String): PluginUpdate? {
        // 仓库索引拉取是 M3 范畴；未配置仓库时返回 null（无更新）。
        db.pluginQueries.selectById(pluginId).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown plugin: $pluginId")
        return null
    }

    /** 索引驱动的更新在 M3；P0 手动更新 = 用户提供新包走 install()。 */
    suspend fun update(pluginId: String): InstalledPlugin {
        throw AppError.CapabilityUnsupported("index-driven update not configured; use install() with package bytes")
    }

    override suspend fun rollback(pluginId: String): InstalledPlugin = withContext(io) {
        val row = db.pluginQueries.selectById(pluginId).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown plugin: $pluginId")
        val prevVersion = row.previous_version
            ?: throw AppError.InvalidInput("no previous version for $pluginId")
        installer.rollback(pluginId)
        db.transaction {
            db.pluginQueries.insertOrReplace(
                id = row.id,
                name = row.name,
                version = prevVersion,
                api_version = row.api_version,
                media_types_json = row.media_types_json,
                capabilities_json = row.capabilities_json,
                manifest_json = row.previous_manifest_json ?: row.manifest_json,
                package_sha256 = row.previous_package_sha256 ?: row.package_sha256,
                signature = row.signature,
                repository_id = row.repository_id,
                relative_install_dir = row.previous_relative_install_dir ?: row.relative_install_dir,
                previous_version = null,
                previous_package_sha256 = null,
                previous_relative_install_dir = null,
                previous_manifest_json = null,
                enabled = row.enabled,
                last_error = null,
                installed_at = row.installed_at,
                updated_at = Clock.System.now().toEpochMilliseconds(),
            )
        }
        InstalledPlugin(row.id, prevVersion, row.enabled == 1L)
    }

    override suspend fun uninstall(pluginId: String): Unit = withContext(io) {
        db.pluginQueries.selectById(pluginId).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown plugin: $pluginId")
        // 先停文件再清 DB；收藏/历史/进度在各自表，不级联删除（快照原则）。
        installer.uninstall(pluginId)
        db.pluginQueries.deleteById(pluginId)
    }
}
