package app.miyoru.domain.download.repository

import app.miyoru.domain.download.model.DownloadRequest
import app.miyoru.domain.download.model.DownloadTask
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeTasks(): Flow<List<DownloadTask>>
    suspend fun enqueue(request: DownloadRequest): String
    suspend fun pause(taskId: String)
    suspend fun resume(taskId: String)
    suspend fun cancel(taskId: String)
    suspend fun remove(taskId: String, deleteFiles: Boolean)
}
