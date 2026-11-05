package app.miyoru.data.local.parse

import app.miyoru.core.common.AppError
import app.miyoru.plugin.api.PackageEntry
import com.fleeksoft.ksoup.Ksoup

/** EPUB 解析（P0）：container → OPF（manifest/spine）→ XHTML 正文提纯。仅 UTF-8。 */
data class EpubChapter(val id: String, val title: String, val text: String)

data class EpubBook(val title: String, val chapters: List<EpubChapter>)

object EpubParser {
    fun parse(entries: List<PackageEntry>): EpubBook {
        val byPath = entries.associate { norm(it.path) to it.bytes }
        val container = byPath["META-INF/container.xml"]?.decodeToString()
            ?: throw AppError.Parse("epub missing container.xml")
        val opfPath = Regex("""full-path\s*=\s*"([^"]+)"""")
            .find(container)?.groupValues?.get(1)
            ?: throw AppError.Parse("epub container has no rootfile")
        val opfDir = opfPath.substringBeforeLast("/", "")
        val opf = byPath[norm(opfPath)]?.decodeToString()
            ?: throw AppError.Parse("epub missing $opfPath")
        val docTitle = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", RegexOption.DOT_MATCHES_ALL)
            .find(opf)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        val manifest = Regex("""<item\s+([^>]*?)/>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(opf).mapNotNull { m ->
                val tag = m.groupValues[1]
                val id = Regex("""id\s*=\s*"([^"]+)""").find(tag)?.groupValues?.get(1)
                val href = Regex("""href\s*=\s*"([^"]+)""").find(tag)?.groupValues?.get(1)
                if (id != null && href != null) id to href else null
            }.toMap()
        val spine = Regex("""<itemref\s+[^>]*idref\s*=\s*"([^"]+)""")
            .findAll(opf).map { it.groupValues[1] }.toList()
        if (spine.isEmpty()) throw AppError.Parse("epub spine empty")
        val chapters = spine.mapIndexed { i, idref ->
            val href = manifest[idref] ?: throw AppError.Parse("epub missing manifest item $idref")
            val full = norm(if (opfDir.isEmpty()) href else "$opfDir/$href")
            val raw = byPath[full] ?: throw AppError.Parse("epub missing $full")
            val doc = Ksoup.parse(raw.decodeToString())
            val heading = doc.select("h1, h2, h3").firstOrNull()?.text()?.takeIf { it.isNotBlank() }
            val text = doc.body().text()
            EpubChapter(idref, heading ?: "第${i + 1}章", text)
        }
        return EpubBook(docTitle ?: "未知标题", chapters)
    }

    private fun norm(path: String): String {
        // 去掉 ./、合并 //，保留大小写（zip 名大小写敏感）
        return path.split("/").filter { it.isNotEmpty() && it != "." }.joinToString("/")
    }
}
