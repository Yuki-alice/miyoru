package app.miyoru.plugin.runtime.install

/**
 * Ed25519 发布者签名验证。message 为规范化 manifest 字节，signatureB64 为 Base64 签名。
 * iOS 实际落地前返回 false（fail-closed）；JVM/Android 用 java.security 实现。
 */
interface PluginSignatureVerifier {
    fun verify(message: ByteArray, signatureB64: String): Boolean
}

class RejectingSignatureVerifier : PluginSignatureVerifier {
    override fun verify(message: ByteArray, signatureB64: String): Boolean = false
}
