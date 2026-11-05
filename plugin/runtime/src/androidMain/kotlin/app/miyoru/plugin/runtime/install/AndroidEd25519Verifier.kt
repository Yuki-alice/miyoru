package app.miyoru.plugin.runtime.install

import android.os.Build
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64

/**
 * Android Ed25519 验证：java.security Ed25519 需 API 33+；低版本抛错，
 * 调用方应在低版本设备上关闭强制签名（enforceSignature=false）或走仓库元数据校验。
 */
class AndroidEd25519Verifier(private val publicKeyBytes: ByteArray) : PluginSignatureVerifier {
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    override fun verify(message: ByteArray, signatureB64: String): Boolean {
        if (Build.VERSION.SDK_INT < 33) {
            throw UnsupportedOperationException("Ed25519 requires API 33+, current=${Build.VERSION.SDK_INT}")
        }
        return try {
            val sig = Signature.getInstance("Ed25519")
            val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(publicKeyBytes))
            sig.initVerify(key)
            sig.update(message)
            sig.verify(Base64.decode(signatureB64))
        } catch (e: Exception) {
            false
        }
    }
}
