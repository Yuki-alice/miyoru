package app.miyoru.data.local.parse

import app.miyoru.core.common.AppError
import app.miyoru.plugin.api.PackageEntry

private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

/** CBZ 解析（P0）：zip 条目按名排序即页序；只收录图片，跳过隐藏文件与目录。 */
data class CbzPage(val name: String, val index: Int)

object CbzParser {
    fun pages(entries: List<PackageEntry>): List<CbzPage> {
        val images = entries
            .filter { !it.path.endsWith("/") }
            .filter { e ->
                val base = e.path.substringAfterLast("/").substringAfterLast("\\")
                !base.startsWith(".") && !e.path.contains("__MACOSX/") &&
                    base.substringAfterLast(".", "").lowercase() in IMAGE_EXTS
            }
            .sortedBy { it.path.lowercase() }
        if (images.isEmpty()) throw AppError.Parse("cbz has no images")
        return images.mapIndexed { i, e -> CbzPage(e.path, i) }
    }
}
