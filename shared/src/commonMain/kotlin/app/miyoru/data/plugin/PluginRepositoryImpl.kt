@file:OptIn(kotlin.time.ExperimentalTime::class)
package app.miyoru.data.plugin


import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.miyoru.core.common.AppError
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.plugin.model.InstalledPlugin
import app.miyoru.domain.plugin.model.PluginUpdate
import app.miyoru.domain.plugin.repository.PluginRepository
import app.miyoru.plugin.api.InstalledPackage
import app.miyoru.plugin.api.PluginInstallerFacade
import app.miyoru.plugin.api.PluginPackageSource
import app.miyoru.plugin.api.compareSemver
import app.miyoru.plugin.api.parseSemver
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
 * 已安装插件的启用、更新查询、回滚与卸载。
 *
 * 单包直装入口已移除：安装只能由 [PluginCatalogRepositoryImpl] 在校验仓库索引后调用
 * [installFromRepository]，且必须绑定 `repository_id`。
 */
class PluginRepositoryImpl(
    private val db: MiyoruDatabase,
    private val installer: PluginInstallerFacade,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : PluginRepository {

    /**
     * 仓库安装链的最后一步：文件系统先行（facade），成功后再提交数据库终态。
     * `repositoryId` 必填；`expectedPluginId` 防止索引条目与实际 manifest 不一致。
     */
    suspend fun installFromRepository(
        pkg: PluginPackageSource,
        repositoryId: String,
        expectedPluginId: String,
        enforceSignature: Boolean,
    ): InstalledPlugin = withContext(io) {
        val installed: InstalledPackage = installer.install(pkg, enforceSignature)
        val m = installed.manifest
        if (m.id != expectedPluginId) {
            installer.uninstall(m.id)
            throw AppError.InvalidInput("manifest id ${m.id} != catalog id $expectedPluginId")
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = db.pluginQueries.selectById(m.id).executeAsOneOrNull()
        if (existing != null && existing.repository_id != repositoryId) {
            // 双保险：跨仓库覆盖必须显式拒绝，不能静默改绑来源。
            installer.rollback(m.id)
            throw AppError.PluginSourceMismatch(m.id)
        }
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
                repository_id = repositoryId,
                relative_install_dir = installer.installDir(m.id),
                previous_version = existing?.version,
                previous_package_sha256 = existing?.package_sha256,
                previous_relative_install_dir = existing?.relative_install_dir,
                previous_manifest_json = existing?.manifest_json,
                enabled = existing?.enabled ?: 1L,
                last_error = null,
                installed_at = existing?.installed_at ?: now,
                updated_at = now,
            )
        }
        InstalledPlugin(m.id, m.version, (existing?.enabled ?: 1L) == 1L, repositoryId)
    }

    override fun observeInstalled(): Flow<List<InstalledPlugin>> =
        db.pluginQueries.selectAll().asFlow().mapToList(io).map { rows ->
            rows.map { InstalledPlugin(it.id, it.version, it.enabled == 1L, it.repository_id) }
        }

    override suspend fun setEnabled(pluginId: String, enabled: Boolean): Unit = withContext(io) {
        db.pluginQueries.selectById(pluginId).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown plugin: $pluginId")
        db.pluginQueries.updateEnabled(if (enabled) 1L else 0L, Clock.System.now().toEpochMilliseconds(), pluginId)
    }

    override suspend fun checkForUpdate(pluginId: String): PluginUpdate? = withContext(io) {
        val row = db.pluginQueries.selectById(pluginId).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown plugin: $pluginId")
        // 空串只可能是 v1→v2 迁移前的历史安装，没有来源仓库因此不可更新。
        if (row.repository_id.isBlank()) return@withContext null
        val cached = db.pluginRepositoryQueries
            .selectCachedPlugin(row.repository_id, pluginId)
            .executeAsOneOrNull() ?: return@withContext null
        val current = parseSemver(row.version) ?: return@withContext null
        val latest = parseSemver(cached.version) ?: return@withContext null
        if (compareSemver(latest, current) > 0) {
            PluginUpdate(pluginId, cached.version, row.repository_id)
        } else {
            null
        }
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
        InstalledPlugin(row.id, prevVersion, row.enabled == 1L, row.repository_id)
    }

    override suspend fun uninstall(pluginId: String): Unit = withContext(io) {
        db.pluginQueries.selectById(pluginId).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown plugin: $pluginId")
        // 先停文件再清 DB；收藏/历史/进度在各自表，不级联删除（快照原则）。
        installer.uninstall(pluginId)
        db.pluginQueries.deleteById(pluginId)
    }
}
