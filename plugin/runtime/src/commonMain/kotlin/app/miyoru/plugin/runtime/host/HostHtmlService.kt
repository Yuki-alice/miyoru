package app.miyoru.plugin.runtime.host

import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.Serializable

@Serializable
data class HostLink(val href: String, val text: String)

/**
 * 宿主 HTML 解析（Ksoup 封装）：只返回可序列化结果，不暴露 DOM/Node 给插件。
 */
object HostHtmlService {
    fun title(html: String): String? =
        Ksoup.parse(html).title().takeIf { it.isNotBlank() }

    fun text(html: String, maxChars: Int = 200_000): String {
        val t = Ksoup.parse(html).body().text()
        return if (t.length > maxChars) t.substring(0, maxChars) else t
    }

    fun links(html: String, limit: Int = 500): List<HostLink> =
        Ksoup.parse(html).select("a[href]").take(limit).map {
            HostLink(href = it.attr("href"), text = it.text())
        }
}
