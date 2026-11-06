package app.miyoru.plugin.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val HEX = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

private fun indexJson(
    schema: Int = 1,
    downloadUrl: String = "https://cdn.example.com/plugins/demo-1.0.0.zip",
    sha: String = HEX,
    extraPlugin: String = "",
): String = """
{
  "schemaVersion": $schema,
  "repositoryId": "demo.repository",
  "name": "Demo Repository",
  "description": "测试仓库",
  "websiteUrl": "https://example.com",
  "iconUrl": "https://example.com/icon.png",
  "generatedAt": 1790000000000,
  "signature": null,
  "futureFieldWillBeIgnored": 42,
  "plugins": [
    {
      "id": "demo.source",
      "name": "Demo Source",
      "version": "1.0.0",
      "apiVersion": 1,
      "minAppVersion": "0.1.0",
      "mediaTypes": ["novel"],
      "capabilities": ["search", "detail"],
      "domains": ["https://example.com"],
      "downloadUrl": "$downloadUrl",
      "packageSha256": "$sha"
    }${if (extraPlugin.isBlank()) "" else ",$extraPlugin"}
  ]
}
""".trimIndent()

class RepositoryIndexTest {

    @Test
    fun `valid index parses and ignores unknown fields`() {
        val index = RepositoryIndexCodec.parse(indexJson().encodeToByteArray(), "0.1.0")
        assertEquals("demo.repository", index.repositoryId)
        assertEquals(1, index.plugins.size)
        assertEquals("demo.source", index.plugins.single().id)
        Unit
    }

    @Test
    fun `canonical bytes ignore signature and key ordering`() {
        val a = RepositoryIndexCodec.decode(indexJson().encodeToByteArray())
        val b = RepositoryIndexCodec.decode(
            indexJson().replace("\"signature\": null,", "").encodeToByteArray(),
        )
        assertEquals(
            RepositoryIndexCodec.fingerprint(a),
            RepositoryIndexCodec.fingerprint(b),
        )
        assertEquals(
            RepositoryIndexCodec.fingerprint(a),
            RepositoryIndexCodec.fingerprint(a.copy(signature = "x".repeat(88))),
        )
        assertEquals(64, RepositoryIndexCodec.fingerprint(a).length)
        Unit
    }

    @Test
    fun `identity fingerprint is stable across plugin updates but changes with signing key`() {
        val a = RepositoryIndexCodec.decode(indexJson().encodeToByteArray())
        val b = a.copy(plugins = a.plugins.map { it.copy(version = "2.0.0") })
        assertEquals(
            RepositoryIndexCodec.identityFingerprint(a, "https://example.com/index.json"),
            RepositoryIndexCodec.identityFingerprint(b, "https://example.com/index.json"),
        )
        val c = a.copy(signingKey = "B".repeat(44))
        assertTrue(
            RepositoryIndexCodec.identityFingerprint(a, "https://example.com/index.json") !=
                RepositoryIndexCodec.identityFingerprint(c, "https://example.com/index.json"),
        )
        assertTrue(
            RepositoryIndexCodec.identityFingerprint(a, "https://example.com/index.json") !=
                RepositoryIndexCodec.identityFingerprint(a, "https://other.example.com/index.json"),
        )
        Unit
    }

    @Test
    fun `unsupported schema version is a distinct error`() {
        val e = assertFailsWith<RepositoryIndexException> {
            RepositoryIndexCodec.parse(indexJson(schema = 99).encodeToByteArray(), "0.1.0")
        }
        assertEquals(REPO_UNSUPPORTED_SCHEMA, e.code)
        assertTrue(e.message!!.contains("update miyoru"))
        Unit
    }

    @Test
    fun `malformed index is invalid`() {
        val e = assertFailsWith<RepositoryIndexException> {
            RepositoryIndexCodec.parse("not json".encodeToByteArray(), "0.1.0")
        }
        assertEquals(REPO_INVALID, e.code)
        Unit
    }

    @Test
    fun `http private and malformed download urls are rejected`() {
        listOf(
            "http://cdn.example.com/a.zip",
            "https://127.0.0.1/a.zip",
            "https://192.168.1.4/a.zip",
            "https://localhost/a.zip",
            "https://user:pw@example.com/a.zip",
            "https://example.com/a.zip#frag",
            "https://example.com:abc/a.zip",
        ).forEach { url ->
            val problems = RepositoryIndexCodec.decode(indexJson(downloadUrl = url).encodeToByteArray())
                .validate("0.1.0")
            assertTrue(problems.any { it.contains("bad downloadUrl") }, "$url should be rejected: $problems")
        }
        Unit
    }

    @Test
    fun `package hash must be 64 hex chars`() {
        val problems = RepositoryIndexCodec.decode(indexJson(sha = "deadbeef").encodeToByteArray())
            .validate("0.1.0")
        assertTrue(problems.any { it.contains("bad packageSha256") })
        Unit
    }

    @Test
    fun `duplicate plugin ids are rejected`() {
        val duplicate = """
        {"id":"demo.source","name":"Dup","version":"1.0.0","apiVersion":1,
         "minAppVersion":"0.1.0","mediaTypes":["novel"],"capabilities":[],
         "domains":["https://example.com"],"downloadUrl":"https://cdn.example.com/d.zip",
         "packageSha256":"$HEX"}
        """.trimIndent()
        val problems = RepositoryIndexCodec.decode(indexJson(extraPlugin = duplicate).encodeToByteArray())
            .validate("0.1.0")
        assertEquals(listOf("duplicate plugin id"), problems)
        Unit
    }

    @Test
    fun `compatibility is a catalog state not an invalid index`() {
        val entry = RepositoryIndexCodec.decode(
            indexJson().replace("\"minAppVersion\": \"0.1.0\"", "\"minAppVersion\": \"9.9.9\"")
                .encodeToByteArray(),
        ).plugins.single()
        val (ok, reason) = entry.isCompatible("0.1.0")
        assertFalse(ok)
        assertTrue(reason!!.contains("9.9.9"))
        Unit
    }

    @Test
    fun `sha256 matches known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc".encodeToByteArray()),
        )
        Unit
    }
}
