package app.miyoru.plugin.runtime.install
import app.miyoru.plugin.api.PackageEntry

import app.miyoru.plugin.api.PluginManifest
import app.miyoru.plugin.api.validate
import kotlinx.serialization.json.Json

/** 纯逻辑校验：zip-slip、manifest 解析/规则、入口存在性与哈希。不碰文件系统。 */
class PluginPackageValidator(
    private val appVersion: String,
    private val enforceSignature: Boolean,
    private val signatureVerifier: PluginSignatureVerifier? = null,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun validate(entries: List<PackageEntry>): ValidatedPackage {
        entries.forEach { e ->
            if (e.path.isBlank() || e.path.startsWith("/") || e.path.startsWith("\\")) {
                throw PackageException(PackageErrorCodes.ZIP_SLIP, "absolute path: ${e.path}")
            }
            val segs = e.path.split("/").filter { it.isNotEmpty() }
            if (segs.isEmpty() || segs.any { it == "." || it == ".." } || e.path.contains("\\")) {
                throw PackageException(PackageErrorCodes.ZIP_SLIP, "bad entry: ${e.path}")
            }
        }
        val manifestBytes = entries.firstOrNull { it.path == "manifest.json" }?.bytes
            ?: throw PackageException(PackageErrorCodes.MISSING_MANIFEST, "manifest.json missing")
        val manifest = try {
            json.decodeFromString<PluginManifest>(manifestBytes.decodeToString())
        } catch (e: Exception) {
            throw PackageException(PackageErrorCodes.BAD_MANIFEST, "manifest parse failed", e)
        }
        val problems = manifest.validate(appVersion, enforceSignature)
        if (problems.isNotEmpty()) {
            throw PackageException(PackageErrorCodes.BAD_MANIFEST, problems.joinToString("; "))
        }
        val entryBytes = entries.firstOrNull { it.path == manifest.entry }?.bytes
            ?: throw PackageException(PackageErrorCodes.MISSING_ENTRY, "entry missing: ${manifest.entry}")
        if (manifest.entrySha256 != null) {
            val actual = Sha256.hex(entryBytes)
            if (!actual.equals(manifest.entrySha256, ignoreCase = true)) {
                throw PackageException(PackageErrorCodes.HASH_MISMATCH, "entry hash mismatch")
            }
        }
        if (enforceSignature) {
            val verifier = signatureVerifier
                ?: throw PackageException(PackageErrorCodes.BAD_SIGNATURE, "no verifier configured")
            val sigB64 = manifest.signature
                ?: throw PackageException(PackageErrorCodes.BAD_SIGNATURE, "signature missing")
            val ok = try {
                verifier.verify(canonicalManifest(manifest).encodeToByteArray(), sigB64)
            } catch (e: Exception) {
                throw PackageException(PackageErrorCodes.BAD_SIGNATURE, "verify failed", e)
            }
            if (!ok) throw PackageException(PackageErrorCodes.BAD_SIGNATURE, "bad signature")
        }
        return ValidatedPackage(manifest, entries)
    }

    /**
     * 签名对象：除 signature 外的 manifest 规范字节（与目标文档 §9.3 一致，
     * 发布者对"不含 signature 字段的规范化 manifest"签名）。
     */
    fun canonicalManifest(manifest: PluginManifest): String {
        val hosts = manifest.permissions.domains.sorted().joinToString(",")
        return listOf(
            manifest.id, manifest.name, manifest.version, manifest.apiVersion.toString(),
            manifest.minAppVersion, manifest.author, manifest.homepage ?: "",
            manifest.mediaTypes.sorted().joinToString(","), manifest.capabilities.sorted().joinToString(","),
            hosts, manifest.entry, manifest.entrySha256 ?: "",
        ).joinToString("\n")
    }
}
