package app.miyoru.data.download

import app.miyoru.core.common.AppError
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.download.model.DownloadTask
import app.miyoru.domain.source.model.ResolvedContent
import app.miyoru.domain.source.repository.SourceRepository
import app.miyoru.platform.security.SecretStore

/**
 * 经 SourceRepository 重解析的真实下载源：普通文件取第一条可用 URL；
 * HLS 取播放列表 URL；secret_ref 的 Header/Cookie 从 SecretStore 取。
 */
class PluginDownloadSource(
    private val db: MiyoruDatabase,
    private val sources: SourceRepository,
    private val secrets: SecretStore,
) : DownloadUrlResolver {
    override suspend fun resolve(taskId: String): ResolvedDownload {
        val row = db.downloadQueries.selectTaskById(taskId).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown task: $taskId")
        val key = MediaKey(SourceId(row.source_id), MediaType.valueOf(row.media_type), RemoteId(row.media_id))
        val unit = RemoteId(row.unit_id ?: throw AppError.InvalidInput("task has no unit: $taskId"))
        val secretHeaders = row.secret_ref?.let { ref ->
            val raw = secrets.get(ref) ?: throw AppError.Storage(Exception("missing secret: $ref"))
            raw.decodeToString().split("\n")
                .mapNotNull { l -> l.substringBefore("=", "").takeIf { it.isNotBlank() }?.let { k -> k to l.substringAfter("=") } }
                .toMap()
        } ?: emptyMap()
        return when (val r = sources.resolve(key, unit)) {
            is ResolvedContent.Novel -> throw AppError.InvalidInput("novel uses text cache, not download engine")
            is ResolvedContent.Comic -> {
                // 漫画离线走逐页普通下载；引擎一次处理一个 URL，取首个（批量由下载队列展开，此处取首个保证行为明确）
                val first = r.pages.firstOrNull() ?: throw AppError.Parse("comic has no pages")
                ResolvedDownload(first.url, secretHeaders + first.headers)
            }
            is ResolvedContent.Video -> {
                val first = r.streams.firstOrNull() ?: throw AppError.Parse("video has no streams")
                ResolvedDownload(first.url, secretHeaders + first.headers)
            }
        }
    }
}

/** 测试/本地导入用的静态源。 */
class StaticDownloadSource(private val table: Map<String, ResolvedDownload>) : DownloadUrlResolver {
    override suspend fun resolve(taskId: String): ResolvedDownload =
        table[taskId] ?: throw AppError.InvalidInput("no url for $taskId")
}
