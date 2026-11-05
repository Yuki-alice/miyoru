package app.miyoru.core.common

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ErrorMappingTest {
    @Test
    fun `codes are stable`() {
        assertEquals(ErrorCode.NETWORK, AppError.Network(RuntimeException()).code)
        assertEquals(ErrorCode.TIMEOUT, AppError.Timeout("op").code)
        assertEquals(ErrorCode.INVALID_INPUT, AppError.InvalidInput("x").code)
        assertEquals(ErrorCode.PERMISSION_DENIED, AppError.PermissionDenied.code)
        Unit
    }

    @Test
    fun `timeout cancellation maps to timeout error`() = runBlocking {
        val timeout = try {
            withTimeout(1) { delay(100) }
            error("should have timed out")
        } catch (e: TimeoutCancellationException) {
            e
        }
        val mapped = timeout.toAppError("search")
        assertIs<AppError.Timeout>(mapped)
        Unit
    }

    @Test
    fun `plain cancellation is rethrown`() {
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            kotlinx.coroutines.CancellationException("gone").toAppError("search")
        }
        Unit
    }

    @Test
    fun `app errors pass through`() {
        val e = AppError.Authentication("s1")
        assertEquals(e, e.toAppError("detail"))
        Unit
    }
}
