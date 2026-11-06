package app.miyoru.plugin.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 索引格式版本；只做兼容演进：新增可选字段，不改名、不改类型、不改语义。 */
const val CURRENT_REPOSITORY_SCHEMA_VERSION = 1
const val MAX_REPOSITORY_PLUGINS = 1000
const val MAX_REPOSITORY_INDEX_BYTES = 2L * 1024 * 1024
const val MAX_PLUGIN_PACKAGE_BYTES = 50L * 1024 * 1024

const val REPO_UNSUPPORTED_SCHEMA = "REPOSITORY_UNSUPPORTED_SCHEMA"
const val REPO_INVALID = "REPOSITORY_INVALID"

class RepositoryIndexException(val code: String, message: String) : Exception(message)

/**
 * miyoru 原生插件仓库索引。一个仓库就是一份第三方托管的 JSON 文件；
 * 应用不内置、不预置、不推荐任何正式仓库。
 *
 * 未知字段一律忽略，以便新版本客户端发布的索引仍能被旧客户端读取。
 */
@Serializable
data class RepositoryIndex(
    val schemaVersion: Int = CURRENT_REPOSITORY_SCHEMA_VERSION,
    val repositoryId: String = "",
    val name: String = "",
    val description: String? = null,
    val websiteUrl: String? = null,
    val iconUrl: String? = null,
    val generatedAt: Long = 0L,
    /** 发布者 Ed25519 公钥（Base64，可选）：参与仓库身份指纹，换公钥必须重新确认信任。 */
    val signingKey: String? = null,
    /** 对「不含 signature 字段的规范化索引字节」的 Ed25519 发布者签名，Base64；可选。 */
    val signature: String? = null,
    val plugins: List<RepositoryPluginEntry> = emptyList(),
)

@Serializable
data class RepositoryPluginEntry(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val apiVersion: Int = 0,
    val minAppVersion: String = "",
    val mediaTypes: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
    /** 插件申请的网络域名，安装前必须向用户展示。 */
    val domains: List<String> = emptyList(),
    val author: String? = null,
    val homepage: String? = null,
    val iconUrl: String? = null,
    val description: String? = null,
    val downloadUrl: String = "",
    val packageSha256: String = "",
)

object RepositoryIndexCodec {
    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val canonical = Json { explicitNulls = false; encodeDefaults = true }

    fun decode(bytes: ByteArray): RepositoryIndex = reader.decodeFromString<RepositoryIndex>(
        bytes.decodeToString(),
    )

    /**
     * 规范化字节序列：去掉 signature 后按声明顺序重排字段。用于索引内容摘要与发布者签名，
     * 避免因 JSON 键顺序或空白差异产生不同指纹。未知字段不参与指纹。
     */
    fun canonicalBytes(index: RepositoryIndex): ByteArray =
        canonical.encodeToString(index.copy(signature = null)).encodeToByteArray()

    /** 索引内容摘要：用于诊断与展示，不作为信任依据（插件版本更新会改变它）。 */
    fun fingerprint(index: RepositoryIndex): String = Sha256.hex(canonicalBytes(index))

    /**
     * 仓库身份指纹：绑定 repositoryId、索引链接与发布者公钥。
     * 插件增删改版本不会改变它，因此正常的索引刷新不需要重新确认；
     * 只有换仓库 ID、换链接或换签名公钥才触发重新确认（对齐 Mihon 信任签名密钥的做法）。
     */
    fun identityFingerprint(index: RepositoryIndex, url: String): String = Sha256.hex(
        "${index.repositoryId}\n${url.trim()}\n${index.signingKey.orEmpty()}".encodeToByteArray(),
    )

    /**
     * 解析 + 校验。失败时抛 [RepositoryIndexException]，code 区分
     * 「需要更新应用」与「索引非法」，调用方据此给出可理解的错误。
     */
    fun parse(bytes: ByteArray, appVersion: String): RepositoryIndex {
        if (bytes.isEmpty()) throw RepositoryIndexException(REPO_INVALID, "empty repository index")
        if (bytes.size > MAX_REPOSITORY_INDEX_BYTES) {
            throw RepositoryIndexException(REPO_INVALID, "repository index too large")
        }
        val index = try {
            decode(bytes)
        } catch (e: Exception) {
            throw RepositoryIndexException(REPO_INVALID, "malformed repository index: ${e.message}")
        }
        if (index.schemaVersion > CURRENT_REPOSITORY_SCHEMA_VERSION) {
            throw RepositoryIndexException(
                REPO_UNSUPPORTED_SCHEMA,
                "repository schemaVersion ${index.schemaVersion} > supported $CURRENT_REPOSITORY_SCHEMA_VERSION; update miyoru",
            )
        }
        val problems = index.validate(appVersion)
        if (problems.isNotEmpty()) {
            throw RepositoryIndexException(REPO_INVALID, problems.joinToString("; "))
        }
        return index
    }
}

/** 返回问题列表；为空表示通过。 */
fun RepositoryIndex.validate(appVersion: String): List<String> {
    val problems = mutableListOf<String>()
    if (schemaVersion < 1) problems += "bad schemaVersion: $schemaVersion"
    if (!ID_RE.matches(repositoryId)) problems += "bad repositoryId: $repositoryId"
    if (name.isBlank() || name.length > 64) problems += "bad repository name"
    if ((description?.length ?: 0) > 500) problems += "repository description too long"
    if (generatedAt < 0L) problems += "bad generatedAt"
    if (plugins.size > MAX_REPOSITORY_PLUGINS) problems += "too many plugins: ${plugins.size}"
    listOfNotNull(websiteUrl?.let { "websiteUrl" to it }, iconUrl?.let { "iconUrl" to it })
        .forEach { (field, value) ->
            if (!isValidHttpsResource(value)) problems += "bad $field: $value"
        }
    if (signature != null && !isPlausibleSignature(signature)) problems += "bad repository signature"
    if (signingKey != null && !isPlausibleKey(signingKey)) problems += "bad repository signingKey"
    if (plugins.map { it.id }.toSet().size != plugins.size) problems += "duplicate plugin id"

    plugins.forEach { entry ->
        val prefix = "plugin ${entry.id.ifBlank { "<empty>" }}"
        if (!ID_RE.matches(entry.id)) problems += "$prefix: bad id"
        if (entry.name.isBlank() || entry.name.length > 64) problems += "$prefix: bad name"
        if (!VERSION_RE.matches(entry.version)) problems += "$prefix: bad version ${entry.version}"
        if (entry.apiVersion !in 1..CURRENT_API_VERSION) problems += "$prefix: unsupported apiVersion ${entry.apiVersion}"
        // 兼容性是一个可展示状态，不是整个索引非法；此处只拒绝格式，目录层标记不兼容条目。
        if (parseSemver(entry.minAppVersion) == null) {
            problems += "$prefix: bad minAppVersion ${entry.minAppVersion}"
        }
        if (entry.mediaTypes.isEmpty() || !entry.mediaTypes.all { it in PLUGIN_MEDIA_TYPES }) {
            problems += "$prefix: bad mediaTypes ${entry.mediaTypes}"
        }
        if (!entry.capabilities.all { it in PLUGIN_CAPABILITIES }) {
            problems += "$prefix: bad capabilities ${entry.capabilities}"
        }
        if (entry.domains.isEmpty() || entry.domains.size > 32) problems += "$prefix: bad domains"
        entry.domains.forEach { d -> if (!isValidHttpsOrigin(d)) problems += "$prefix: bad domain $d" }
        if (!isValidHttpsResource(entry.downloadUrl)) problems += "$prefix: bad downloadUrl ${entry.downloadUrl}"
        if (!HEX64_RE.matches(entry.packageSha256)) problems += "$prefix: bad packageSha256"
        listOfNotNull(
            entry.homepage?.let { "homepage" to it },
            entry.iconUrl?.let { "iconUrl" to it },
        ).forEach { (field, value) ->
            if (!isValidHttpsResource(value)) problems += "$prefix: bad $field: $value"
        }
        if ((entry.description?.length ?: 0) > 500) problems += "$prefix: description too long"
    }
    return problems
}

private fun isPlausibleKey(base64: String): Boolean {
    if (base64.length !in 40..64) return false
    return base64.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }
}

/** 索引里的插件是否可被当前应用安装（与「索引是否合法」分开，不兼容条目仍要展示）。 */
fun RepositoryPluginEntry.isCompatible(appVersion: String): Pair<Boolean, String?> {
    if (apiVersion !in 1..CURRENT_API_VERSION) return false to "需要新版应用（apiVersion $apiVersion）"
    val min = parseSemver(minAppVersion)
    val app = parseSemver(appVersion)
    if (min == null) return false to "版本信息无效"
    if (app != null && compareSemver(min, app) > 0) return false to "需要应用版本 $minAppVersion 或更高"
    return true to null
}

/**
 * 严格的 HTTPS 资源地址：拒绝 http、userinfo、fragment、空白、非数字端口与私网字面 IP。
 * 域名规则复用 [isValidHttpsOrigin]，保证与插件权限白名单一致。
 */
fun isValidHttpsResource(raw: String): Boolean {
    if (raw.isBlank() || raw != raw.trim() || raw.length > 512) return false
    if (!raw.startsWith("https://")) return false
    if ('#' in raw || '\\' in raw || '\n' in raw || '\r' in raw) return false
    val authority = raw.removePrefix("https://").substringBefore("/")
    if (authority.isEmpty() || '@' in authority) return false
    val host = authority.substringBefore(":")
    val port = authority.substringAfter(":", "")
    if (port.isNotEmpty() && (port.toIntOrNull() == null || port.toInt() !in 1..65535)) return false
    return isValidHttpsOrigin("https://$host")
}

