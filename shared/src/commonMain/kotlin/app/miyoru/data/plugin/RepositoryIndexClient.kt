package app.miyoru.data.plugin

import app.miyoru.core.common.AppError
import app.miyoru.plugin.api.MAX_PLUGIN_PACKAGE_BYTES
import app.miyoru.plugin.api.MAX_REPOSITORY_INDEX_BYTES
import app.miyoru.plugin.api.isValidHttpsResource
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

private const val MAX_REDIRECTS = 3

/**
 * 仓库索引与插件包的唯一网络入口。
 *
 * 强制 HTTPS、禁止私网字面 IP / userinfo / fragment，逐跳重新校验重定向目标，
 * 并对响应体设硬上限。索引缓存可以离线读，但刷新与安装必须重新经过这里。
 */
class RepositoryIndexClient(
    http: HttpClient,
) {
    // Ktor 3.6 只在 client 级别提供 followRedirects；克隆一个关闭自动跳转的客户端，
    // 以便逐跳校验重定向目标（共享 engine，不在应用生命周期内关闭）。
    private val http: HttpClient = http.config { followRedirects = false }
    suspend fun fetchIndex(url: String): ByteArray = fetch(url, MAX_REPOSITORY_INDEX_BYTES)

    suspend fun fetchPackage(url: String): ByteArray = fetch(url, MAX_PLUGIN_PACKAGE_BYTES)

    private suspend fun fetch(rawUrl: String, maxBytes: Long): ByteArray {
        var current = rawUrl.trim()
        if (!isValidHttpsResource(current)) {
            throw AppError.InvalidInput("only https repository urls are allowed: $rawUrl")
        }
        repeat(MAX_REDIRECTS + 1) { hop ->
            if (!isValidHttpsResource(current)) {
                throw AppError.InvalidInput("unsafe redirect target")
            }
            var payload = ByteArray(0)
            var redirect: String? = null
            http.prepareGet(current).execute { response ->
                if (response.status.value in 300..399) {
                    redirect = response.headers[HttpHeaders.Location]
                    // 消费掉 3xx 的响应体，避免连接泄漏。
                    response.bodyAsChannel().readRemaining(1024).readByteArray()
                } else {
                    if (!response.status.isSuccess()) {
                        throw AppError.Network(Exception("http ${response.status.value}"))
                    }
                    payload = response.bodyAsChannel().readRemaining(maxBytes + 1).readByteArray()
                }
            }
            val next = redirect
            if (next.isNullOrBlank()) {
                if (payload.size > maxBytes) throw AppError.InvalidInput("response too large")
                if (payload.isEmpty()) throw AppError.Parse("empty response")
                return payload
            }
            if (hop == MAX_REDIRECTS) throw AppError.InvalidInput("too many redirects")
            current = resolveRedirect(current, next)
        }
        throw AppError.InvalidInput("too many redirects")
    }
}

/** 只处理 HTTPS 绝对地址与常见的相对 Location，不使用平台 URL 类。 */
internal fun resolveRedirect(current: String, location: String): String {
    val target = location.trim()
    val base = current.removePrefix("https://")
    val authority = base.substringBefore("/")
    return when {
        target.startsWith("https://") -> target
        target.startsWith("http://") -> target // 下一跳的协议校验会拒绝
        target.startsWith("/") -> "https://$authority$target"
        else -> {
            val parent = base.substringBeforeLast("/", "")
            val prefix = if (parent.isEmpty()) "https://$authority" else "https://$parent"
            "$prefix/$target"
        }
    }
}
