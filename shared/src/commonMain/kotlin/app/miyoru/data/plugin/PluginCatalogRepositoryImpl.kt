@file:OptIn(kotlin.time.ExperimentalTime::class)
package app.miyoru.data.plugin

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.miyoru.core.common.AppError
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.plugin.model.CatalogEntry
import app.miyoru.domain.plugin.model.CatalogEntryState
import app.miyoru.domain.plugin.model.InstalledPlugin
import app.miyoru.domain.plugin.model.PluginCatalog
import app.miyoru.domain.plugin.model.PluginRepo
import app.miyoru.domain.plugin.model.PluginUpdate
import app.miyoru.domain.plugin.model.RefreshOutcome
import app.miyoru.domain.plugin.model.RepositoryGroup
import app.miyoru.domain.plugin.model.RepositoryPreview
import app.miyoru.domain.plugin.repository.PluginCatalogRepository
import app.miyoru.plugin.api.MAX_PLUGIN_PACKAGE_BYTES
import app.miyoru.plugin.api.REPO_UNSUPPORTED_SCHEMA
import app.miyoru.plugin.api.RepositoryIndex
import app.miyoru.plugin.api.RepositoryIndexCodec
import app.miyoru.plugin.api.RepositoryIndexException
import app.miyoru.plugin.api.RepositoryPluginEntry
import app.miyoru.plugin.api.Sha256
import app.miyoru.plugin.api.PluginPackageSource
import app.miyoru.plugin.api.isCompatible
import app.miyoru.plugin.api.isValidHttpsResource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private val catalogJson = Json { ignoreUnknownKeys = true }

/**
 * 插件仓库目录的实现：识别/信任仓库 → 缓存已验证索引 → 按仓库浏览 → 按需安装/更新。
 *
 * 关键不变量：
 * - 未信任的识别结果只在 [RepositoryPreviewStore] 内存里，绝不落库。
 * - 身份指纹（仓库 ID + 链接 + 发布者公钥）变化不静默应用，要求用户重新识别并确认（防仓库劫持）。
 * - 插件包必须先与索引 `packageSha256` 比对，再交给 [app.miyoru.plugin.api.PluginInstallerFacade]。
 * - 已安装插件只能从 `PLUGIN.repository_id` 指向的仓库更新，跨仓库覆盖被拒绝。
 * - 刷新失败保留上一次已验证的索引缓存，离线仍可浏览目录。
 */
class PluginCatalogRepositoryImpl(
    private val db: MiyoruDatabase,
    private val client: RepositoryIndexClient,
    private val previewStore: RepositoryPreviewStore,
    private val pluginRepository: PluginRepositoryImpl,
    private val appVersion: String,
    private val enforceSignature: Boolean = false,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : PluginCatalogRepository {

    override fun observeRepositories(): Flow<List<PluginRepo>> =
        db.pluginRepositoryQueries.selectAllRepositories().asFlow().mapToList(io).map { rows ->
            rows.map { r ->
                PluginRepo(
                    id = r.id, url = r.url, name = r.name,
                    description = r.description, websiteUrl = r.website_url, iconUrl = r.icon_url,
                    schemaVersion = r.schema_version.toInt(), fingerprint = r.fingerprint,
                    enabled = r.enabled == 1L, lastUpdated = r.last_updated,
                    lastError = r.last_error, pluginCount = r.plugin_count.toInt(),
                )
            }
        }

    override fun observeCatalog(): Flow<PluginCatalog> = combine(
        observeRepositories(),
        db.pluginRepositoryQueries.selectAllCachedPlugins().asFlow().mapToList(io),
        db.pluginQueries.selectAll().asFlow().mapToList(io),
    ) { repos, cachedRows, installedRows ->
        val installedById = installedRows.associateBy { it.id }
        val repoIds = repos.map { it.id }.toSet()
        val groups = repos.map { repo ->
            val entries = cachedRows.filter { it.repository_id == repo.id }.map { c ->
                val installed = installedById[c.plugin_id]
                val (state, reason) = stateOf(
                    repositoryEnabled = repo.enabled,
                    repositoryId = repo.id,
                    installedRepositoryId = installed?.repository_id,
                    installedVersion = installed?.version,
                    remoteVersion = c.version,
                    apiVersion = c.api_version,
                    minAppVersion = c.min_app_version,
                )
                CatalogEntry(
                    pluginId = c.plugin_id,
                    repositoryId = c.repository_id,
                    name = c.name,
                    version = c.version,
                    mediaTypes = catalogJson.decodeFromString(c.media_types_json),
                    capabilities = catalogJson.decodeFromString(c.capabilities_json),
                    domains = catalogJson.decodeFromString(c.domains_json),
                    description = c.description,
                    installedVersion = installed?.version,
                    state = state,
                    reason = reason,
                )
            }
            RepositoryGroup(repo, entries)
        }
        val updates = cachedRows.mapNotNull { c ->
            val installed = installedById[c.plugin_id] ?: return@mapNotNull null
            if (installed.repository_id != c.repository_id) return@mapNotNull null
            if (!isNewer(c.version, installed.version)) return@mapNotNull null
            PluginUpdate(c.plugin_id, c.version, c.repository_id)
        }
        val orphans = installedRows
            .filter { it.repository_id.isBlank() || it.repository_id !in repoIds }
            .map { InstalledPlugin(it.id, it.version, it.enabled == 1L, it.repository_id) }
        PluginCatalog(groups, updates, orphans)
    }

    override suspend fun inspectRepository(url: String): RepositoryPreview = withContext(io) {
        val normalized = url.trim()
        if (!isValidHttpsResource(normalized)) {
            throw AppError.InvalidInput("仓库链接必须是 HTTPS 地址")
        }
        val index = parseIndex(client.fetchIndex(normalized))
        val existing = db.pluginRepositoryQueries
            .selectRepositoryById(index.repositoryId).executeAsOneOrNull()
        if (existing != null && existing.url != normalized) {
            throw AppError.InvalidInput("仓库 ID ${index.repositoryId} 已绑定到另一个链接")
        }
        val fingerprint = RepositoryIndexCodec.identityFingerprint(index, normalized)
        previewStore.put(PendingRepository(normalized, index, fingerprint))
        RepositoryPreview(
            url = normalized,
            repositoryId = index.repositoryId,
            name = index.name,
            description = index.description,
            websiteUrl = index.websiteUrl,
            iconUrl = index.iconUrl,
            schemaVersion = index.schemaVersion,
            fingerprint = fingerprint,
            pluginCount = index.plugins.size,
            hasSignature = index.signature != null,
            alreadyAdded = existing != null && existing.fingerprint != fingerprint,
        )
    }

    override suspend fun addRepository(fingerprint: String): PluginRepo = withContext(io) {
        val pending = previewStore.take(fingerprint)
            ?: throw AppError.RepositoryUntrusted(fingerprint)
        val index = pending.index
        val existing = db.pluginRepositoryQueries
            .selectRepositoryById(index.repositoryId).executeAsOneOrNull()
        if (existing != null && existing.url != pending.url) {
            throw AppError.InvalidInput("仓库 ID ${index.repositoryId} 已绑定到另一个链接")
        }
        applyIndex(
            pending = pending,
            enabled = existing?.enabled ?: 1L,
            createdAt = existing?.created_at ?: Clock.System.now().toEpochMilliseconds(),
            signingKey = existing?.signing_key,
        )
        PluginRepo(
            id = index.repositoryId, url = pending.url, name = index.name,
            description = index.description, websiteUrl = index.websiteUrl, iconUrl = index.iconUrl,
            schemaVersion = index.schemaVersion, fingerprint = pending.fingerprint,
            enabled = (existing?.enabled ?: 1L) == 1L,
            lastUpdated = Clock.System.now().toEpochMilliseconds(),
            lastError = null, pluginCount = index.plugins.size,
        )
    }

    override suspend fun removeRepository(id: String, removePlugins: Boolean): Unit = withContext(io) {
        if (removePlugins) {
            val rows = db.pluginQueries.selectByRepository(id).executeAsList()
            rows.forEach { row -> pluginRepository.uninstall(row.id) }
        }
        db.transaction {
            db.pluginRepositoryQueries.deleteCachedPluginsByRepository(id)
            db.pluginRepositoryQueries.deleteRepositoryById(id)
        }
    }

    override suspend fun setRepositoryEnabled(id: String, enabled: Boolean): Unit = withContext(io) {
        db.pluginRepositoryQueries.selectRepositoryById(id).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown repository: $id")
        db.pluginRepositoryQueries.updateRepositoryEnabled(
            if (enabled) 1L else 0L,
            Clock.System.now().toEpochMilliseconds(),
            id,
        )
    }

    override suspend fun refreshRepository(id: String): RefreshOutcome = withContext(io) {
        val row = db.pluginRepositoryQueries.selectRepositoryById(id).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown repository: $id")
        val bytes = try {
            client.fetchIndex(row.url)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val message = e.message ?: "网络失败"
            db.pluginRepositoryQueries.updateRepositoryError(message, id)
            return@withContext RefreshOutcome(failed = listOf("$id: $message"))
        }
        val index = try {
            parseIndex(bytes)
        } catch (e: AppError) {
            db.pluginRepositoryQueries.updateRepositoryError(e.message ?: "索引非法", id)
            return@withContext RefreshOutcome(failed = listOf("$id: ${e.message}"))
        }
        if (index.repositoryId != id) {
            db.pluginRepositoryQueries.updateRepositoryError("索引 repositoryId 与仓库不一致", id)
            return@withContext RefreshOutcome(failed = listOf("$id: repositoryId 不一致"))
        }
        val fingerprint = RepositoryIndexCodec.identityFingerprint(index, row.url)
        if (fingerprint != row.fingerprint) {
            // 不静默替换执行代码来源；保留旧缓存，要求用户重新识别并确认信任。
            db.pluginRepositoryQueries.updateRepositoryError("身份指纹已变化（仓库 ID、链接或发布者公钥变更），请重新识别并确认信任", id)
            return@withContext RefreshOutcome(fingerprintChanged = listOf(id))
        }
        applyIndex(
            pending = PendingRepository(row.url, index, fingerprint),
            enabled = row.enabled,
            createdAt = row.created_at,
            signingKey = row.signing_key,
        )
        RefreshOutcome(refreshed = 1, updateCount = checkForUpdates().size)
    }

    override suspend fun refreshAll(): RefreshOutcome = withContext(io) {
        val ids = db.pluginRepositoryQueries.selectAllRepositories().executeAsList().map { it.id }
        val outcome = RefreshOutcome()
        var refreshed = 0
        val failed = mutableListOf<String>()
        val changed = mutableListOf<String>()
        ids.forEach { id ->
            val single = try {
                refreshRepository(id)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                RefreshOutcome(failed = listOf("$id: ${e.message}"))
            }
            refreshed += single.refreshed
            failed += single.failed
            changed += single.fingerprintChanged
        }
        outcome.copy(
            refreshed = refreshed,
            failed = failed,
            fingerprintChanged = changed,
            updateCount = checkForUpdates().size,
        )
    }

    override suspend fun install(repositoryId: String, pluginId: String): InstalledPlugin = withContext(io) {
        val repo = db.pluginRepositoryQueries.selectRepositoryById(repositoryId).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown repository: $repositoryId")
        if (repo.enabled != 1L) throw AppError.InvalidInput("仓库已停用：$repositoryId")
        val cached = db.pluginRepositoryQueries.selectCachedPlugin(repositoryId, pluginId)
            .executeAsOneOrNull() ?: throw AppError.InvalidInput("仓库中没有插件 $pluginId")
        val existing = db.pluginQueries.selectById(pluginId).executeAsOneOrNull()
        if (existing != null && existing.repository_id != repositoryId) {
            throw AppError.PluginSourceMismatch(pluginId)
        }
        val (compatible, reason) = compatibility(cached.api_version, cached.min_app_version)
        if (!compatible) throw AppError.PluginIncompatible(pluginId, reason ?: "不兼容")

        val bytes = client.fetchPackage(cached.download_url)
        if (bytes.size > MAX_PLUGIN_PACKAGE_BYTES) throw AppError.InvalidInput("package too large")
        val actualSha = Sha256.hex(bytes)
        if (!actualSha.equals(cached.package_sha256, ignoreCase = true)) {
            throw AppError.InvalidInput("插件包哈希与仓库索引不一致")
        }
        val fileName = cached.download_url.substringBefore("?").substringAfterLast("/")
            .takeIf { it.isNotBlank() } ?: "$pluginId.zip"
        pluginRepository.installFromRepository(
            pkg = PluginPackageSource(fileName, bytes),
            repositoryId = repositoryId,
            expectedPluginId = pluginId,
            enforceSignature = enforceSignature,
        )
    }

    override suspend fun update(pluginId: String): InstalledPlugin = withContext(io) {
        val row = db.pluginQueries.selectById(pluginId).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown plugin: $pluginId")
        if (row.repository_id.isBlank()) throw AppError.PluginSourceMismatch(pluginId)
        if (pluginRepository.checkForUpdate(pluginId) == null) {
            throw AppError.InvalidInput("$pluginId 已是最新版本")
        }
        install(row.repository_id, pluginId)
    }

    override suspend fun checkForUpdates(): List<PluginUpdate> = withContext(io) {
        db.pluginQueries.selectAll().executeAsList().mapNotNull { row ->
            try {
                pluginRepository.checkForUpdate(row.id)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
    }

    override suspend fun catalogEntry(repositoryId: String, pluginId: String): CatalogEntry? = withContext(io) {
        val repo = db.pluginRepositoryQueries.selectRepositoryById(repositoryId).executeAsOneOrNull()
            ?: return@withContext null
        val c = db.pluginRepositoryQueries.selectCachedPlugin(repositoryId, pluginId)
            .executeAsOneOrNull() ?: return@withContext null
        val installed = db.pluginQueries.selectById(pluginId).executeAsOneOrNull()
        val (state, reason) = stateOf(
            repositoryEnabled = repo.enabled == 1L,
            repositoryId = repositoryId,
            installedRepositoryId = installed?.repository_id,
            installedVersion = installed?.version,
            remoteVersion = c.version,
            apiVersion = c.api_version,
            minAppVersion = c.min_app_version,
        )
        CatalogEntry(
            pluginId = c.plugin_id,
            repositoryId = c.repository_id,
            name = c.name,
            version = c.version,
            mediaTypes = catalogJson.decodeFromString(c.media_types_json),
            capabilities = catalogJson.decodeFromString(c.capabilities_json),
            domains = catalogJson.decodeFromString(c.domains_json),
            description = c.description,
            installedVersion = installed?.version,
            state = state,
            reason = reason,
        )
    }

    private fun parseIndex(bytes: ByteArray): RepositoryIndex = try {
        RepositoryIndexCodec.parse(bytes, appVersion)
    } catch (e: RepositoryIndexException) {
        if (e.code == REPO_UNSUPPORTED_SCHEMA) {
            throw AppError.InvalidInput("需要更新应用才能使用此仓库：${e.message}")
        }
        throw AppError.RepositoryInvalid(e.message ?: "仓库索引不合法")
    }

    private suspend fun applyIndex(
        pending: PendingRepository,
        enabled: Long,
        createdAt: Long,
        signingKey: String?,
    ) {
        val index = pending.index
        val now = Clock.System.now().toEpochMilliseconds()
        db.transaction {
            db.pluginRepositoryQueries.insertRepository(
                id = index.repositoryId,
                url = pending.url,
                name = index.name,
                description = index.description,
                website_url = index.websiteUrl,
                icon_url = index.iconUrl,
                schema_version = index.schemaVersion.toLong(),
                signing_key = index.signingKey ?: signingKey,
                fingerprint = pending.fingerprint,
                enabled = enabled,
                last_updated = now,
                last_error = null,
                plugin_count = index.plugins.size.toLong(),
                created_at = createdAt,
            )
            db.pluginRepositoryQueries.deleteCachedPluginsByRepository(index.repositoryId)
            index.plugins.forEach { e ->
                db.pluginRepositoryQueries.insertOrReplaceCachedPlugin(
                    repository_id = index.repositoryId,
                    plugin_id = e.id,
                    name = e.name,
                    version = e.version,
                    api_version = e.apiVersion.toLong(),
                    min_app_version = e.minAppVersion,
                    media_types_json = catalogJson.encodeToString(e.mediaTypes),
                    capabilities_json = catalogJson.encodeToString(e.capabilities),
                    domains_json = catalogJson.encodeToString(e.domains),
                    author = e.author,
                    homepage = e.homepage,
                    icon_url = e.iconUrl,
                    description = e.description,
                    download_url = e.downloadUrl,
                    package_sha256 = e.packageSha256,
                )
            }
        }
    }

    private fun compatibility(apiVersion: Long, minAppVersion: String): Pair<Boolean, String?> =
        RepositoryPluginEntry(apiVersion = apiVersion.toInt(), minAppVersion = minAppVersion)
            .isCompatible(appVersion)

    private fun stateOf(
        repositoryEnabled: Boolean,
        repositoryId: String,
        installedRepositoryId: String?,
        installedVersion: String?,
        remoteVersion: String,
        apiVersion: Long,
        minAppVersion: String,
    ): Pair<CatalogEntryState, String?> {
        if (installedRepositoryId != null && installedRepositoryId != repositoryId) {
            return CatalogEntryState.SOURCE_MISMATCH to "已由另一个仓库安装，需先卸载"
        }
        val (compatible, reason) = compatibility(apiVersion, minAppVersion)
        if (!compatible) return CatalogEntryState.INCOMPATIBLE to reason
        if (!repositoryEnabled) return CatalogEntryState.REPOSITORY_DISABLED to "仓库已停用"
        if (installedVersion == null) return CatalogEntryState.AVAILABLE to null
        if (isNewer(remoteVersion, installedVersion)) {
            return CatalogEntryState.UPDATE_AVAILABLE to "$installedVersion → $remoteVersion"
        }
        return CatalogEntryState.INSTALLED to null
    }

    private fun isNewer(remote: String, installed: String): Boolean {
        val a = app.miyoru.plugin.api.parseSemver(remote) ?: return false
        val b = app.miyoru.plugin.api.parseSemver(installed) ?: return false
        return app.miyoru.plugin.api.compareSemver(a, b) > 0
    }
}
