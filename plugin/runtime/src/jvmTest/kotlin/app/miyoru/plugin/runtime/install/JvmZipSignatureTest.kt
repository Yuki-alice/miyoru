package app.miyoru.plugin.runtime.install

import app.miyoru.plugin.api.PluginManifest
import app.miyoru.plugin.api.zip.CommonZipReader
import app.miyoru.plugin.api.PluginPermissions
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import app.miyoru.plugin.api.PackageEntry

private val json = Json { ignoreUnknownKeys = true }

private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val bos = ByteArrayOutputStream()
    ZipOutputStream(bos).use { zos ->
        entries.forEach { (name, bytes) ->
            zos.putNextEntry(ZipEntry(name))
            zos.write(bytes)
            zos.closeEntry()
        }
    }
    return bos.toByteArray()
}

class JvmZipSignatureTest {
    @Test
    fun `zip round-trip`() = runBlocking {
        val bundle = "globalThis.search = 1;".encodeToByteArray()
        val entries = CommonZipReader.read(zipOf("manifest.json" to "{}".encodeToByteArray(), "bundle.js" to bundle))
        assertEquals(2, entries.size)
        assertEquals(bundle.toList(), entries.first { it.path == "bundle.js" }.bytes.toList())
        Unit
    }

    @Test
    fun `zip slip entry passes through for validator to reject`() = runBlocking {
        val entries = CommonZipReader.read(zipOf("../../evil.js" to byteArrayOf(1)))
        // 解包层不拦截，由 Validator 拒绝（职责分离可测）
        assertEquals("../../evil.js", entries.single().path)
        Unit
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `ed25519 sign and verify`() {
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        val kp = kpg.generateKeyPair()
        val message = "example.source\nExample\n1.0.0".encodeToByteArray()
        val sig = Signature.getInstance("Ed25519").apply {
            initSign(kp.private); update(message)
        }.sign()
        val verifier = JvmEd25519Verifier(kp.public.encoded)
        assertTrue(verifier.verify(message, Base64.encode(sig)))
        assertFalse(verifier.verify("tampered".encodeToByteArray(), Base64.encode(sig)))
        Unit
    }

    @Test
    fun `validator enforces signature with jvm verifier`() {
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        val kp = kpg.generateKeyPair()
        val bundle = "globalThis.search = 1;".encodeToByteArray()
        val manifest = PluginManifest(
            id = "example.source", name = "Example", version = "1.0.0", apiVersion = 1,
            minAppVersion = "0.1.0", mediaTypes = listOf("novel"), capabilities = listOf("search"),
            permissions = PluginPermissions(listOf("https://example.com")),
            entry = "bundle.js", entrySha256 = Sha256.hex(bundle), signature = "PLACEHOLDER",
        )
        val canonical = PluginPackageValidator("0.1.0", true).canonicalManifest(manifest)
        val sig = Signature.getInstance("Ed25519").apply {
            initSign(kp.private); update(canonical.encodeToByteArray())
        }.sign()
        val signed = manifest.copy(signature = Base64.encode(sig))
        val entries = listOf(
            PackageEntry("manifest.json", json.encodeToString(signed).encodeToByteArray()),
            PackageEntry("bundle.js", bundle),
        )
        val validated = PluginPackageValidator(
            "0.1.0", enforceSignature = true,
            signatureVerifier = JvmEd25519Verifier(kp.public.encoded),
        ).validate(entries)
        assertEquals("1.0.0", validated.manifest.version)
        Unit
    }
}
