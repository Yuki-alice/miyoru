@file:OptIn(kotlin.time.ExperimentalTime::class)
package app.miyoru.data.download


import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.miyoru.core.common.AppError
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.download.model.DownloadRequest
import app.miyoru.domain.download.model.DownloadTask
import app.miyoru.domain.download.repository.DownloadRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class DownloadRepositoryImpl(
    private val db: MiyoruDatabase,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : DownloadRepository {

    override fun observeTasks(): Flow<List<DownloadTask>> =
        db.downloadQueries.selectAllTasks().asFlow().mapToList(io).map { rows ->
            rows.map { DownloadTask(it.id, it.state) }
        }

    override suspend fun enqueue(request: DownloadRequest): String = withContext(io) {
        // request_json 只存非敏感选择器，不存最终临时 URL（结构文档 §7.3）。
        val id = "dl_${Clock.System.now().toEpochMilliseconds()}_${request.mediaId.hashCode()}"
        db.downloadQueries.insertTask(
            id = id,
            source_id = "",
            media_type = "",
            media_id = request.mediaId,
            unit_id = request.unitId,
            kind = request.kind,
            request_json = "{}",
            secret_ref = null,
            state = "QUEUED",
            downloaded_bytes = 0,
            total_bytes = null,
            relative_path = null,
            error_code = null,
            retry_count = 0,
            created_at = Clock.System.now().toEpochMilliseconds(),
            updated_at = Clock.System.now().toEpochMilliseconds(),
        )
        id
    }

    override suspend fun pause(taskId: String) = setState(taskId, "PAUSED")

    override suspend fun resume(taskId: String): Unit = withContext(io) {
        val row = db.downloadQueries.selectTaskById(taskId).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown task: $taskId")
        if (row.state != "PAUSED" && row.state != "FAILED") {
            throw AppError.InvalidInput("cannot resume task in state ${row.state}")
        }
        setState(taskId, "QUEUED")
    }

    override suspend fun cancel(taskId: String) = setState(taskId, "CANCELED")

    override suspend fun remove(taskId: String, deleteFiles: Boolean): Unit = withContext(io) {
        db.transaction {
            // 文件删除由 DownloadEngine/B5 按 relative_path 执行，这里只清索引；
            // deleteFiles=true 时调用方须先停任务再删文件（结构文档 §10.2）。
            db.downloadQueries.deleteTask(taskId)
        }
    }

    private suspend fun setState(taskId: String, state: String): Unit = withContext(io) {
        val row = db.downloadQueries.selectTaskById(taskId).executeAsOneOrNull()
            ?: throw AppError.InvalidInput("unknown task: $taskId")
        db.downloadQueries.updateTaskState(
            state = state,
            downloaded_bytes = row.downloaded_bytes,
            total_bytes = row.total_bytes,
            relative_path = row.relative_path,
            error_code = row.error_code,
            updated_at = Clock.System.now().toEpochMilliseconds(),
            id = taskId,
        )
    }
}
