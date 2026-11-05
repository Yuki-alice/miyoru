package app.miyoru.plugin.runtime.install

import app.miyoru.plugin.api.PluginManifest
import app.miyoru.plugin.api.PackageEntry

/** 解包后（未解压前）的包条目：相对路径 + 原始字节。解压由平台侧完成。 */
data class ValidatedPackage(val manifest: PluginManifest, val entries: List<PackageEntry>) {
    fun entryBytes(name: String): ByteArray =
        entries.firstOrNull { it.path == name }?.bytes
            ?: throw PackageException(PackageErrorCodes.MISSING_ENTRY, "missing: $name")
}

object PackageErrorCodes {
    const val ZIP_SLIP = "ZIP_SLIP"
    const val MISSING_MANIFEST = "MISSING_MANIFEST"
    const val BAD_MANIFEST = "BAD_MANIFEST"
    const val MISSING_ENTRY = "MISSING_ENTRY"
    const val HASH_MISMATCH = "HASH_MISMATCH"
    const val BAD_SIGNATURE = "BAD_SIGNATURE"
}

class PackageException(val code: String, message: String, cause: Throwable? = null) : Exception(message, cause)
