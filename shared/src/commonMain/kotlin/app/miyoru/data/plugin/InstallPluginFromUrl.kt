package app.miyoru.data.plugin

import app.miyoru.core.common.AppError
import app.miyoru.plugin.api.InstalledPackage
import app.miyoru.plugin.api.PluginInstallerFacade
import app.miyoru.plugin.api.PluginPackageSource
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/** 从 URL 下载插件包并安装（P0 手动更新路径；仓库索引在 M3）。 */
class InstallPluginFromUrl(
    private val http: HttpClient,
    private val installer: PluginInstallerFacade,
    private val maxBytes: Long = 50L * 1024 * 1024,
) {
    suspend fun install(url: String, enforceSignature: Boolean = false): InstalledPackage {
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            throw AppError.InvalidInput("bad url: $url")
        }
        val bytes = ByteArray(0).let {
            var out = it
            http.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) throw AppError.Network(Exception("http ${response.status.value}"))
                out = response.bodyAsChannel().readRemaining(maxBytes + 1).readByteArray()
            }
            out
        }
        if (bytes.size > maxBytes) throw AppError.InvalidInput("package too large")
        if (bytes.isEmpty()) throw AppError.Parse("empty package")
        val name = url.substringBefore("?").substringAfterLast("/").takeIf { it.isNotBlank() } ?: "plugin.zip"
        return installer.install(PluginPackageSource(name, bytes), enforceSignature)
    }
}
