package app.miyoru.plugin.runtime

import app.miyoru.plugin.runtime.host.HostHttpCodes
import app.miyoru.plugin.runtime.host.HostHttpConfig
import app.miyoru.plugin.runtime.host.HostHttpException
import app.miyoru.plugin.runtime.host.HostHttpService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun service(
    hosts: Set<String> = setOf("allowed.test"),
    maxBytes: Long = 1024,
    withCookies: Boolean = false,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): Pair<HostHttpService, MutableList<String?>> {
    val seenCookies = mutableListOf<String?>()
    val engine = MockEngine { request ->
        seenCookies.add(request.headers[HttpHeaders.Cookie])
        handler(request)
    }
    val client = HttpClient(engine) {
        followRedirects = false
        if (withCookies) install(HttpCookies)
    }
    return HostHttpService(HostHttpConfig("p1", hosts, maxResponseBytes = maxBytes), client) to seenCookies
}

class HostHttpServiceTest {
    @Test
    fun `allowed https get returns body`() {
        runBlocking {
            val (svc) = service { respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK) }
            val res = svc.get("https://allowed.test/ok")
            assertEquals(200, res.status)
            assertEquals(3, res.body.size)
            Unit
        }
    }

    @Test
    fun `disallowed domain blocked`() {
        runBlocking {
            val (svc) = service { respond("x", HttpStatusCode.OK) }
            val err = assertFailsWith<HostHttpException> { svc.get("https://evil.test/x") }
            assertEquals(HostHttpCodes.DOMAIN_NOT_ALLOWED, err.code)
            Unit
        }
    }

    @Test
    fun `http and private hosts blocked`() {
        runBlocking {
            val (svc) = service { respond("x", HttpStatusCode.OK) }
            assertEquals(HostHttpCodes.BAD_PROTOCOL, assertFailsWith<HostHttpException> { svc.get("http://allowed.test/x") }.code)
            assertEquals(HostHttpCodes.PRIVATE_HOST, assertFailsWith<HostHttpException> { svc.get("https://127.0.0.1/x") }.code)
            Unit
        }
    }

    @Test
    fun `redirect hop revalidated`() {
        runBlocking {
            val (svc) = service { req ->
                when (req.url.encodedPath) {
                    "/redir" -> respond("", HttpStatusCode.Found, headersOf("Location", "https://allowed.test/target"))
                    "/evil" -> respond("", HttpStatusCode.Found, headersOf("Location", "https://evil.test/"))
                    else -> respond("hi".encodeToByteArray(), HttpStatusCode.OK)
                }
            }
            val ok = svc.get("https://allowed.test/redir")
            assertEquals("https://allowed.test/target", ok.finalUrl)
            val err = assertFailsWith<HostHttpException> { svc.get("https://allowed.test/evil") }
            assertEquals(HostHttpCodes.DOMAIN_NOT_ALLOWED, err.code)
            Unit
        }
    }

    @Test
    fun `oversize body rejected`() {
        runBlocking {
            val (svc) = service(maxBytes = 4) { respond(ByteArray(100), HttpStatusCode.OK) }
            assertEquals(HostHttpCodes.TOO_LARGE, assertFailsWith<HostHttpException> { svc.get("https://allowed.test/big") }.code)
            Unit
        }
    }

    @Test
    fun `cookies isolated per client`() {
        runBlocking {
            val (svc1, seen1) = service(withCookies = true) { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "s=1; Path=/")) }
            svc1.get("https://allowed.test/cookie")
            svc1.get("https://allowed.test/cookie")
            assertTrue(seen1.last() != null, "second request on same client should carry cookie")

            val (svc2, seen2) = service(withCookies = true) { respond("{}", HttpStatusCode.OK) }
            svc2.get("https://allowed.test/other")
            assertNull(seen2.single(), "fresh client must not carry other plugin's cookie")
            Unit
        }
    }
}
