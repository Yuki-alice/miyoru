package app.miyoru.platform.security

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosKeychainTest {
    @Test
    fun roundTrip() = runBlocking {
        val store = IosKeychainSecretStore()
        store.delete("k1")
        assertNull(store.get("k1"))
        store.put("k1", "v1".encodeToByteArray())
        assertEquals("v1", store.get("k1")?.decodeToString())
        store.put("k1", "v2".encodeToByteArray())
        assertEquals("v2", store.get("k1")?.decodeToString())
        store.delete("k1")
        assertNull(store.get("k1"))
        Unit
    }
}
