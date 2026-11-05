package app.miyoru.data.download

import app.miyoru.core.database.InMemoryJvmDriverFactory
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.download.model.DownloadRequest
import app.miyoru.domain.download.model.DownloadTask
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.io.files.Path
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun wipeDb(path: Path) {
    val meta = SystemFileSystem.metadataOrNull(path) ?: return
    if (meta.isDirectory) SystemFileSystem.list(path).forEach { wipeDb(it) }
    SystemFileSystem.delete(path, mustExist = false)
}

class EngineHlsTest {
    private val payload = ByteArray(100 * 1024) { (it % 251).toByte() }

    private fun client() = HttpClient(MockEngine) {
        followRedirects = false
        engine {
            addHandler { request ->
                val url = request.url.toString()
                when {
                    url.endsWith("file.bin") -> {
                        val range = request.headers[HttpHeaders.Range]
                        if (range != null) {
                            val start = range.removePrefix("bytes=").substringBefore("-").toLong()
                            respond(
                                payload.copyOfRange(start.toInt(), payload.size),
                                HttpStatusCode.PartialContent,
                                headersOf(HttpHeaders.ContentLength to listOf((payload.size - start).toString())),
                            )
                        } else {
                            respond(payload, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength to listOf(payload.size.toString())))
                        }
                    }
                    url.endsWith("list.m3u8") -> respond(
                        ("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n" +
                            "#EXTINF:6.0,\nseg0.ts\n#EXTINF:6.0,\nseg1.ts\n#EXT-X-ENDLIST\n").encodeToByteArray(),
                        HttpStatusCode.OK,
                    )
                    url.endsWith(".ts") -> respond("SEGMENT".encodeToByteArray(), HttpStatusCode.OK)
                    else -> respond("nf", HttpStatusCode.NotFound)
                }
            }
        }
    }

    @Test
    fun `plain download completes with exact bytes`() {
        runBlocking {
            wipeDb(Path("build", "tmp", "dl", "plain"))
            val db = MiyoruDatabase(InMemoryJvmDriverFactory().createDriver())
            val repo = DownloadRepositoryImpl(db)
            val id = repo.enqueue(DownloadRequest("m1", "u1", "cover"))
            val eng = HttpDownloadEngine(
                db, client(),
                StaticDownloadSource(mapOf(id to ResolvedDownload("http://cdn.test/file.bin"))),
                "build/tmp/dl/plain",
            )
            eng.enqueue(id).collect { }
            assertEquals("COMPLETED", repo.observeTasks().first().single().state)
            val bytes = SystemFileSystem.source(Path("build/tmp/dl/plain/$id/file.bin")).let {
                it.buffered().use { src -> src.readByteArray() }
            }
            assertEquals(payload.toList(), bytes.toList())
            Unit
        }
    }

    @Test
    fun `hls downloads segments and rewrites playlist`() {
        runBlocking {
            wipeDb(Path("build", "tmp", "dl", "hls"))
            val db = MiyoruDatabase(InMemoryJvmDriverFactory().createDriver())
            val repo = DownloadRepositoryImpl(db)
            val id = repo.enqueue(DownloadRequest("v1", "e1", "hls"))
            val eng = HttpDownloadEngine(
                db, client(),
                StaticDownloadSource(mapOf(id to ResolvedDownload("http://cdn.test/list.m3u8"))),
                "build/tmp/dl/hls",
            )
            eng.enqueue(id).collect { }
            assertEquals("COMPLETED", repo.observeTasks().first().single().state)
            assertTrue(SystemFileSystem.exists(Path("build/tmp/dl/hls/$id/index.m3u8")))
            assertTrue(SystemFileSystem.exists(Path("build/tmp/dl/hls/$id/seg00000.ts")))
            val index = SystemFileSystem.source(Path("build/tmp/dl/hls/$id/index.m3u8")).let {
                it.buffered().use { src -> src.readByteArray().decodeToString() }
            }
            assertTrue(index.contains("seg00001.ts") && index.contains("#EXT-X-ENDLIST"))
            Unit
        }
    }

    @Test
    fun `cancel stops and resume continues with range`() {
        runBlocking {
            wipeDb(Path("build", "tmp", "dl", "cancel"))
            val slow = HttpClient(MockEngine) {
                followRedirects = false
                engine {
                    addHandler {
                        delay(50)
                        respond(payload, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength to listOf(payload.size.toString())))
                    }
                }
            }
            val db = MiyoruDatabase(InMemoryJvmDriverFactory().createDriver())
            val repo = DownloadRepositoryImpl(db)
            val id = repo.enqueue(DownloadRequest("m1", "u1", "cover"))
            val eng = HttpDownloadEngine(db, slow, StaticDownloadSource(mapOf(id to ResolvedDownload("http://cdn.test/file.bin"))), "build/tmp/dl/cancel")
            val job = launch { eng.enqueue(id).collect { } }
            delay(120)
            eng.cancel(id)
            job.join()
            // 取消后任务仍可恢复（状态机在 Repository 侧置 PAUSED/QUEUED）
            repo.pause(id)
            assertEquals("PAUSED", repo.observeTasks().first().single().state)
            repo.resume(id)
            val eng2 = HttpDownloadEngine(db, client(), StaticDownloadSource(mapOf(id to ResolvedDownload("http://cdn.test/file.bin"))), "build/tmp/dl/cancel")
            eng2.enqueue(id).collect { }
            assertEquals("COMPLETED", repo.observeTasks().first().single().state)
            val bytes = SystemFileSystem.source(Path("build/tmp/dl/cancel/$id/file.bin")).let {
                it.buffered().use { src -> src.readByteArray() }
            }
            assertEquals(payload.size, bytes.size)
            Unit
        }
    }
}
