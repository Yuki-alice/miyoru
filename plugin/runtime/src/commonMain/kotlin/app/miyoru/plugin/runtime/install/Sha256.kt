package app.miyoru.plugin.runtime.install

/**
 * 兼容门面：SHA-256 的唯一实现已上移到 `plugin:api`，让 shared 的仓库安装链
 * 也能在安装包之前比对索引里的 packageSha256。这里只保留包名以稳定既有调用点。
 */
object Sha256 {
    fun digest(input: ByteArray): ByteArray = app.miyoru.plugin.api.Sha256.digest(input)
    fun hex(input: ByteArray): String = app.miyoru.plugin.api.Sha256.hex(input)
}
