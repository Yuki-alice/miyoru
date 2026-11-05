package app.miyoru.platform.download

import kotlinx.coroutines.flow.Flow

data class DownloadEvent(
    val taskId: String,
    val state: String,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
)

interface DownloadEngine {
    fun enqueue(taskId: String): Flow<DownloadEvent>
    suspend fun pause(taskId: String)
    suspend fun resume(taskId: String)
    suspend fun cancel(taskId: String)
}
