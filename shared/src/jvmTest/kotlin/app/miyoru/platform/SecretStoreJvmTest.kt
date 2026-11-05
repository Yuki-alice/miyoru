package app.miyoru.platform

import app.miyoru.platform.security.JvmSecretStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SecretStoreJvmTest {
    @Test
    fun `put get delete round-trip`() {
        runBlocking {
            val store = JvmSecretStore()
            store.put("k1.test", "v1".encodeToByteArray())
            assertEquals("v1", store.get("k1.test")?.decodeToString())
            store.delete("k1.test")
            assertNull(store.get("k1.test"))
            Unit
        }
    }
}
