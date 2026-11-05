package app.miyoru.plugin.runtime.install

import java.security.Signature
import java.util.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** JVM Ed25519 验证（java.security，JDK 15+）。 */
class JvmEd25519Verifier(private val publicKeyBytes: ByteArray) : PluginSignatureVerifier {
    @OptIn(ExperimentalEncodingApi::class)
    override fun verify(message: ByteArray, signatureB64: String): Boolean {
        return try {
            val sig = Signature.getInstance("Ed25519")
            val keySpec = java.security.spec.X509EncodedKeySpec(publicKeyBytes)
            val key = java.security.KeyFactory.getInstance("Ed25519").generatePublic(keySpec)
            sig.initVerify(key)
            sig.update(message)
            sig.verify(kotlin.io.encoding.Base64.decode(signatureB64))
        } catch (e: Exception) {
            false
        }
    }
}
