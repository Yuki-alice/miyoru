package app.miyoru.plugin.runtime.install

/**
 * iOS 占位：fail-closed（始终 false）。正式实现（Security.framework / CryptoKit）
 * 在 iOS 平台集成轮落地；此前 iOS 安装必须 enforceSignature=false。
 */
class IosSignatureVerifier : PluginSignatureVerifier {
    override fun verify(message: ByteArray, signatureB64: String): Boolean = false
}
