package app.miyoru.plugin.runtime

import app.miyoru.plugin.api.PluginCall
import app.miyoru.plugin.api.PluginErrorCodes
import app.miyoru.plugin.api.PluginException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickJsPluginEngineTest {
    private fun engine(timeoutMs: Long = 10_000, maxBytes: Int = 2 * 1024 * 1024) =
        QuickJsPluginEngine(PluginEngineConfig(timeoutMs = timeoutMs, maxResultBytes = maxBytes))

    @Test
    fun `invoke capability returns json`() {
        runBlocking {
            val engine = engine()
            val s = engine.createSession("p1", "globalThis.search = function(p){ return {q: p.q}; };")
            val res = s.invoke(PluginCall("search", mapOf("q" to "hello")))
            assertTrue(res.ok)
            assertTrue(res.json.contains("hello"))
            s.close()
            Unit
        }
    }

    @Test
    fun `missing capability maps to unsupported`() {
        runBlocking {
            val engine = engine()
            val s = engine.createSession("p1", "var x = 1;")
            val res = s.invoke(PluginCall("search", emptyMap()))
            assertFalse(res.ok)
            assertEquals(PluginErrorCodes.CAPABILITY_UNSUPPORTED, res.errorCode)
            s.close()
            Unit
        }
    }

    @Test
    fun `slow capability maps to timeout and session survives`() {
        runBlocking {
            val engine = engine(timeoutMs = 500)
            val s = engine.createSession("p1", "globalThis.slow = function(){ while(true){} }; globalThis.fast = function(){ return 1; };")
            val res = s.invoke(PluginCall("slow", emptyMap()))
            assertFalse(res.ok)
            assertEquals(PluginErrorCodes.TIMEOUT, res.errorCode)
            val ok = s.invoke(PluginCall("fast", emptyMap()))
            assertTrue(ok.ok)
            s.close()
            Unit
        }
    }

    @Test
    fun `oversize result rejected`() {
        runBlocking {
            val engine = engine(maxBytes = 16)
            val s = engine.createSession("p1", "globalThis.big = function(){ return \"0123456789abcdef\"; };")
            val res = s.invoke(PluginCall("big", emptyMap()))
            assertFalse(res.ok)
            assertEquals(PluginErrorCodes.TOO_LARGE, res.errorCode)
            s.close()
            Unit
        }
    }

    @Test
    fun `throwing bundle fails session creation`() {
        runBlocking {
            val engine = engine()
            try {
                engine.createSession("p1", "throw new Error('broken');")
                error("should have thrown")
            } catch (e: PluginException) {
                assertEquals(PluginErrorCodes.LOAD_ERROR, e.code)
            }
            Unit
        }
    }
}
