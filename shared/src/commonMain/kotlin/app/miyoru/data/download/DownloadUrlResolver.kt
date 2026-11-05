package app.miyoru.data.download

/** 任务执行前重解析最终 URL（不持久化临时流地址，结构文档 §7.3）。 */
data class ResolvedDownload(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
)

fun interface DownloadUrlResolver {
    suspend fun resolve(taskId: String): ResolvedDownload
}
