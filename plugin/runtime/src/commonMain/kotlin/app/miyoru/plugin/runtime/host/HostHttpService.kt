package app.miyoru.plugin.runtime.host

import app.miyoru.plugin.api.isPrivateIpLiteral
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

data class HostHttpConfig(
    val pluginId: String,
    /** manifest origins（`https://host[/prefix]`）的主机集合。 */
    val allowedHosts: Set<String>,
    val maxResponseBytes: Long = 10L * 1024 * 1024,
    val maxRedirects: Int = 5,
    val allowHttp: Boolean = false,
)

class HostHttpException(val code: String, message: String, cause: Throwable? = null) : Exception(message, cause)

data class HostHttpResponse(
    val finalUrl: String,
    val status: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
)

object HostHttpCodes {
    const val DOMAIN_NOT_ALLOWED = "DOMAIN_NOT_ALLOWED"
    const val BAD_PROTOCOL = "BAD_PROTOCOL"
    const val PRIVATE_HOST = "PRIVATE_HOST"
    const val BAD_URL = "BAD_URL"
    const val TOO_LARGE = "TOO_LARGE"
    const val TIMEOUT = "TIMEOUT"
    const val TOO_MANY_REDIRECTS = "TOO_MANY_REDIRECTS"
    const val BAD_REDIRECT = "BAD_REDIRECT"
}

/**
 * 插件宿主 HTTP：域名白名单、协议、重定向逐跳校验、私网拦截、响应大小上限。
 * Cookie 隔离由调用方保证：每个插件使用独立 HttpClient + 独立 CookieStorage。
 */
class HostHttpService(
    private val config: HostHttpConfig,
    private val client: HttpClient,
) {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HostHttpResponse {
        var current = parseAndCheck(url)
        var hops = 0
        while (true) {
            val target = current
            val call: HttpResponse = try {
                client.request(target.toString()) {
                    method = HttpMethod.Get
                    headers.forEach { (k, v) -> header(k, v) }
                }
            } catch (e: HttpRequestTimeoutException) {
                throw HostHttpException(HostHttpCodes.TIMEOUT, "timeout: ${target.host}", e)
            }
            val status = call.status.value
            if (status in 300..399) {
                if (++hops > config.maxRedirects) {
                    throw HostHttpException(HostHttpCodes.TOO_MANY_REDIRECTS, "too many redirects: $url")
                }
                val location = call.headers["Location"]
                    ?: throw HostHttpException(HostHttpCodes.BAD_REDIRECT, "missing location: $target")
                current = parseAndCheck(resolveAgainst(target, location))
                continue
            }
            val channel = call.bodyAsChannel()
            val packet = channel.readRemaining(config.maxResponseBytes + 1)
            val bytes = packet.readByteArray()
            if (bytes.size > config.maxResponseBytes) {
                throw HostHttpException(HostHttpCodes.TOO_LARGE, "response too large: ${target.host}")
            }
            return HostHttpResponse(
                finalUrl = target.toString(),
                status = status,
                headers = call.headers.entries().associate { it.key to it.value },
                body = bytes,
            )
        }
    }

    private fun parseAndCheck(raw: String): Url {
        val parsed = try {
            Url(raw)
        } catch (e: Exception) {
            throw HostHttpException(HostHttpCodes.BAD_URL, "bad url", e)
        }
        val isHttps = parsed.protocol == URLProtocol.HTTPS
        val isHttp = parsed.protocol == URLProtocol.HTTP
        if (!isHttps && !(isHttp && config.allowHttp)) {
            throw HostHttpException(HostHttpCodes.BAD_PROTOCOL, "protocol not allowed: ${parsed.protocol.name}")
        }
        val host = parsed.host.lowercase()
        if (host == "localhost" || isPrivateIpLiteral(host)) {
            throw HostHttpException(HostHttpCodes.PRIVATE_HOST, "private host: $host")
        }
        val allowed = config.allowedHosts.any { a ->
            val ah = a.lowercase()
            host == ah || host.endsWith(".$ah")
        }
        if (!allowed) throw HostHttpException(HostHttpCodes.DOMAIN_NOT_ALLOWED, "domain not allowed: $host")
        return parsed
    }

    internal fun resolveAgainst(base: Url, location: String): String {
        if (location.startsWith("http://") || location.startsWith("https://")) return location
        // 协议相对 URL：沿用当前协议
        if (location.startsWith("//")) return "${base.protocol.name}:$location"
        val query = location.substringAfter("?", "").substringBefore("#")
        val pathPart = location.substringBefore("?").substringBefore("#")
        val merged = if (pathPart.startsWith("/")) {
            pathPart
        } else {
            val dir = base.pathSegments.dropLast(1).joinToString("/")
            "$dir/$pathPart"
        }
        val normalized = normalizePath(merged)
        val origin = buildString {
            append(base.protocol.name)
            append("://")
            append(base.host)
            if (base.port != 0 && base.port != base.protocol.defaultPort) append(":${base.port}")
        }
        return buildString {
            append(origin)
            append(normalized)
            if (query.isNotEmpty()) {
                append("?")
                append(query)
            }
        }
    }

    private fun normalizePath(path: String): String {
        val out = mutableListOf<String>()
        for (seg in path.split("/")) {
            when (seg) {
                "", "." -> if (out.isEmpty()) out.add("")
                ".." -> if (out.size > 1) out.removeLast()
                else -> out.add(seg)
            }
        }
        val joined = out.joinToString("/")
        return joined.ifEmpty { "/" }
    }
}
