package app.miyoru.plugin.api

/** 解包后的包条目：相对路径 + 原始字节。解压由平台侧 ZipReader 实现。 */
data class PackageEntry(val path: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is PackageEntry && path == other.path && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = 31 * path.hashCode() + bytes.contentHashCode()
}

fun interface ZipReader {
    suspend fun read(zipBytes: ByteArray): List<PackageEntry>
}
