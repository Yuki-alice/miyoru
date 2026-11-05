package app.miyoru.plugin.runtime

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class QuickJsSmokeTest {

    private fun engine() = QuickJs.create(Dispatchers.Default)

    @Test
    fun `evaluate arithmetic`() = runBlocking {
        val qjs = engine()
        try {
            assertEquals(2, qjs.evaluate("1 + 1", "t.js"))
        } finally {
            qjs.close()
        }
    }

    @Test
    fun `host binding is callable from JS`() = runBlocking {
        val qjs = engine()
        try {
            qjs.function("add") { args ->
                (args[0] as Number).toInt() + (args[1] as Number).toInt()
            }
            assertEquals(5, qjs.evaluate("add(2, 3)", "t.js"))
        } finally {
            qjs.close()
        }
    }

    @Test
    fun `infinite loop is interrupted by native evaluation timeout`() = runBlocking {
        val qjs = engine()
        try {
            qjs.evaluationTimeoutMillis = 1_000
            val mark = TimeSource.Monotonic.markNow()
            val err = try {
                qjs.evaluate<Int>("while (true) {}", "loop.js")
                null
            } catch (e: Exception) {
                e
            }
            val elapsed = mark.elapsedNow()
            assertTrue(err != null, "expected timeout exception, got result")
            assertTrue(elapsed < 10.seconds, "engine did not interrupt loop: $elapsed")
            println("timeout exception: ${err!!::class.simpleName}: ${err.message}")
            // 引擎未被破坏
            assertEquals(2, qjs.evaluate("1 + 1", "t.js"))
        } finally {
            qjs.close()
        }
    }

    @Test
    fun `manual interrupt stops evaluation`() = runBlocking {
        val qjs = engine()
        try {
            val err = coroutineScope {
                val deferred = async(Dispatchers.Default) {
                    try {
                        qjs.evaluate<Int>("while (true) {}", "loop.js")
                        null
                    } catch (e: Exception) {
                        e
                    }
                }
                delay(500)
                qjs.interruptEvaluation()
                withTimeout(10.seconds) { deferred.await() }
            }
            assertTrue(err != null, "expected interrupt exception, got result")
            println("interrupt exception: ${err!!::class.simpleName}: ${err.message}")
            assertEquals(2, qjs.evaluate("1 + 1", "t.js"))
        } finally {
            qjs.close()
        }
    }
}
