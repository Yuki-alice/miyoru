@file:OptIn(kotlin.time.ExperimentalTime::class)
package app.miyoru.data.download


import app.miyoru.core.common.AppError
import app.miyoru.data.download.hls.parseHlsPlaylist
import app.miyoru.data.download.hls.resolveHlsUri
import app.miyoru.db.MiyoruDatabase
import app.miyoru.platform.download.DownloadEngine
import app.miyoru.platform.download.DownloadEvent
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write
import kotlin.time.Clock

/**
 * 普通 HTTP（Range 断点续传、.part 临时文件、原子改名）+ HLS 基础离线
 * （分片逐个下载、本地 index.m3u8 重写）。
 *
 * - 任务状态机由 Repository（DB）拥有；引擎只执行并回写进度。
 * - 每次执行前经 DownloadUrlResolver 重解析 URL（临时地址不复用）。
 * - 取消：协程取消 + 任务状态由调用方经 Repository 置 CANCELED/PAUSED。
 */
class HttpDownloadEngine(
    private val db: MiyoruDatabase,
    private val client: HttpClient,
    private val resolver: DownloadUrlResolver,
    /** 下载根目录（相对 CWD 或绝对；测试传 build/tmp）。 */
    private val rootDir: String,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : DownloadEngine {
    private val jobs = mutableMapOf<String, Job>()
    private val lock = Mutex()
    private val events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 64)

    override fun enqueue(taskId: String): Flow<DownloadEvent> = flow {
        val job = coroutineScope {
            launch {
                try {
                    runTask(taskId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    markFailed(taskId, e)
                    throw e
                }
            }
        }
        lock.withLock { jobs[taskId] = job }
        try {
            job.join()
        } finally {
            lock.withLock { jobs.remove(taskId) }
        }
        events.emit(DownloadEvent(taskId, currentState(taskId)))
    }

    override suspend fun pause(taskId: String) {
        lock.withLock { jobs[taskId] }?.cancel()
    }

    override suspend fun resume(taskId: String) {
        // 状态机在 Repository；引擎 resume = 重新 enqueue（Range 续传）。
        enqueue(taskId).collect { }
    }

    override suspend fun cancel(taskId: String) {
        lock.withLock { jobs[taskId] }?.cancel()
    }

    private suspend fun runTask(taskId: String) {
        setState(taskId, "RUNNING", null)
        val resolved = resolver.resolve(taskId)
        if (isHlsUrl(resolved.url)) downloadHls(taskId, resolved.url, resolved.headers)
        else downloadPlain(taskId, resolved.url, resolved.headers)
        setState(taskId, "COMPLETED", null)
    }

    private fun isHlsUrl(url: String): Boolean {
        val lower = url.substringBefore("?").lowercase()
        return lower.endsWith(".m3u8")
    }

    private suspend fun downloadPlain(taskId: String, url: String, headers: Map<String, String>) {
        val dir = taskDir(taskId)
        SystemFileSystem.createDirectories(Path(dir))
        val partPath = Path(dir, "file.part")
        val done = if (SystemFileSystem.exists(partPath)) {
            SystemFileSystem.source(partPath).buffered().use { it.readByteArray().size.toLong() }
        } else 0L
        client.prepareGet(url) {
            headers.forEach { (k, v) -> header(k, v) }
            if (done > 0) header(HttpHeaders.Range, "bytes=$done-")
        }.execute { response ->
            if (!response.status.isSuccess() && response.status.value != 206) {
                throw AppError.Network(Exception("http ${response.status.value}"))
            }
            val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let {
                if (response.status.value == 206) it + done else it
            }
            updateProgress(taskId, done, total, null)
            val channel = response.bodyAsChannel()
            val sink = SystemFileSystem.sink(partPath, append = done > 0).buffered()
            var received = done
            var lastDbWrite = received
            sink.use {
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                    if (packet.exhausted()) break
                    val bytes = packet.readByteArray()
                    it.write(bytes)
                    it.flush()
                    received += bytes.size
                    if (received - lastDbWrite >= 512 * 1024) {
                        lastDbWrite = received
                        updateProgress(taskId, received, total, null)
                    }
                }
            }
            updateProgress(taskId, received, total ?: received, finalName(url))
            val final = Path(dir, finalName(url))
            SystemFileSystem.atomicMove(partPath, final)
        }
    }

    private suspend fun downloadHls(taskId: String, playlistUrl: String, headers: Map<String, String>) {
        val dir = taskDir(taskId)
        SystemFileSystem.createDirectories(Path(dir))
        val playlistText = fetchText(playlistUrl, headers)
        val playlist = parseHlsPlaylist(playlistText)
        updateProgress(taskId, 0, playlist.segments.size.toLong(), "index.m3u8")
        val localNames = mutableListOf<String>()
        playlist.segments.forEachIndexed { index, seg ->
            val segUrl = resolveHlsUri(playlistUrl, seg.uri)
            val name = "seg" + index.toString().padStart(5, '0') + ".ts"
            val out = Path(dir, name)
            if (!SystemFileSystem.exists(out)) {
                val bytes = fetchBytes(segUrl, headers)
                SystemFileSystem.sink(out).buffered().use { sink -> sink.write(bytes) }
            }
            localNames.add(name)
            updateProgress(taskId, (index + 1).toLong(), playlist.segments.size.toLong(), "index.m3u8")
        }
        val localIndex = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:${playlist.targetDurationSecs.toInt().coerceAtLeast(1)}")
            playlist.segments.forEachIndexed { i, s ->
                appendLine("#EXTINF:${s.durationSecs},")
                appendLine(localNames[i])
            }
            appendLine("#EXT-X-ENDLIST")
        }
        SystemFileSystem.sink(Path(dir, "index.m3u8")).buffered().use { it.write(localIndex.encodeToByteArray()) }
    }

    private suspend fun fetchText(url: String, headers: Map<String, String>): String {
        var out = ""
        client.prepareGet(url) {
            headers.forEach { (k, v) -> header(k, v) }
        }.execute { response ->
            if (!response.status.isSuccess()) throw AppError.Network(Exception("http ${response.status.value}"))
            out = response.bodyAsChannel().readRemaining(Long.MAX_VALUE).readByteArray().decodeToString()
        }
        return out
    }

    private suspend fun fetchBytes(url: String, headers: Map<String, String>): ByteArray {
        var out = ByteArray(0)
        client.prepareGet(url) {
            headers.forEach { (k, v) -> header(k, v) }
        }.execute { response ->
            if (!response.status.isSuccess()) throw AppError.Network(Exception("http ${response.status.value}"))
            out = response.bodyAsChannel().readRemaining(Long.MAX_VALUE).readByteArray()
        }
        return out
    }

    private fun taskDir(taskId: String) = "$rootDir/$taskId"

    private fun finalName(url: String): String {
        val last = url.substringBefore("?").substringAfterLast("/")
        return last.takeIf { it.isNotBlank() } ?: "file.bin"
    }

    private fun updateProgress(taskId: String, downloaded: Long, total: Long?, relativePath: String?) {
        val row = db.downloadQueries.selectTaskById(taskId).executeAsOneOrNull() ?: return
        db.downloadQueries.updateTaskState(
            state = row.state,
            downloaded_bytes = downloaded,
            total_bytes = total ?: row.total_bytes,
            relative_path = relativePath ?: row.relative_path,
            error_code = row.error_code,
            updated_at = Clock.System.now().toEpochMilliseconds(),
            id = taskId,
        )
    }

    private fun setState(taskId: String, state: String, error: String?) {
        val row = db.downloadQueries.selectTaskById(taskId).executeAsOneOrNull() ?: return
        db.downloadQueries.updateTaskState(
            state = state, downloaded_bytes = row.downloaded_bytes, total_bytes = row.total_bytes,
            relative_path = row.relative_path, error_code = error,
            updated_at = Clock.System.now().toEpochMilliseconds(), id = taskId,
        )
        events.tryEmit(DownloadEvent(taskId, state, row.downloaded_bytes, row.total_bytes))
    }

    private fun markFailed(taskId: String, e: Exception) {
        val code = if (e is AppError) e::class.simpleName ?: "Error" else e::class.simpleName ?: "Error"
        setState(taskId, "FAILED", code)
    }

    private fun currentState(taskId: String): String =
        db.downloadQueries.selectTaskById(taskId).executeAsOneOrNull()?.state ?: "UNKNOWN"

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8192
    }
}
