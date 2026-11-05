package app.miyoru.data.local.parse

/** TXT 解析（P0）：只认 UTF-8（含 BOM）；章节按空行分隔的标题行启发式切分。 */
data class TxtChapter(val title: String, val startOffset: Int)

data class TxtBook(val title: String, val text: String, val chapters: List<TxtChapter>)

private val CHAPTER_RE = Regex(
    """^\s*(第[一二三四五六七八九十百千万\d\s]{1,10}[章节回集卷].{0,28}|第\d+章.{0,28}|Chapter\s+\d+.{0,28}|CHAPTER\s+\d+.{0,28}|\d+\s*[.、]\s*.{1,28}|序章.*|序言.*|楔子.*|终章.*|后记.*|番外.*)\s*$"""
)

object TxtParser {
    fun parse(fileName: String, bytes: ByteArray): TxtBook {
        var text = bytes.decodeToString().trimStart('\uFEFF')
        if (text.isEmpty()) throw app.miyoru.core.common.AppError.Parse("empty txt: $fileName")
        // 统一换行
        text = text.replace("\r\n", "\n").replace("\r", "\n")
        val lines = text.lines()
        val chapters = mutableListOf<TxtChapter>()
        var offset = 0
        val lineStarts = IntArray(lines.size)
        var acc = 0
        lines.forEachIndexed { i, l ->
            lineStarts[i] = acc
            acc += l.length + 1
        }
        // 正文开始前的连续空行跳过；标题行单独成行且匹配章节模式
        lines.forEachIndexed { i, line ->
            val t = line.trim()
            if (t.isNotEmpty() && t.length <= 40 && CHAPTER_RE.matches(t)) {
                // 避免把正文短句误判：要求前后有空行或是文件首个非空行
                val prevBlank = i == 0 || lines[i - 1].isBlank()
                val nextBlank = i + 1 >= lines.size || lines[i + 1].isBlank()
                val firstContent = chapters.isEmpty() && lines.subList(0, i).all { it.isBlank() }
                if ((prevBlank && nextBlank) || firstContent) {
                    chapters.add(TxtChapter(t, lineStarts[i]))
                }
            }
        }
        val title = fileName.substringBeforeLast(".")
            .takeIf { it.isNotBlank() } ?: fileName
        return TxtBook(title, text, chapters)
    }
}
