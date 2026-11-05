package app.miyoru.platform.security

import kotlinx.coroutines.runBlocking
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosKeychainDiagTest {
    @Test
    fun `diagnose environment`() = runBlocking {
        val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull()
        println("DIAG documents=$docs")
        assertTrue(docs != null)
        val store = IosKeychainSecretStore()
        store.delete("diag")
        val v = store.get("diag")
        println("DIAG get-missing=$v (expect null)")
        assertEquals(null, v)
        Unit
    }
}
