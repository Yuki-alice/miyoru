package app.miyoru.data

import app.miyoru.plugin.api.PackageEntry
import app.miyoru.plugin.runtime.install.Sha256
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun wipeRoot(path: Path) {
    val meta = SystemFileSystem.metadataOrNull(path) ?: return
    if (meta.isDirectory) SystemFileSystem.list(path).forEach { wipeRoot(it) }
    SystemFileSystem.delete(path, mustExist = false)
}

fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
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

const val FIXTURE_BUNDLE = """
globalThis.search = function(p){ return {items:[{id:"r1",title:"T1",cover:"https://example.com/y.jpg",author:"A"}], nextCursor: null}; };
globalThis.detail = function(p){ return {title:"T1",cover:null,author:"A",description:"D",status:"S",tags:[],attributes:{}}; };
globalThis.chapters = function(p){ return {units:[{id:"u1",title:"C1",index:0}]}; };
globalThis.content = function(p){ return {text:"hello"}; };
"""

fun fixtureManifest(bundle: ByteArray, version: String = "1.0.0"): ByteArray = """
{"id":"test.source","name":"Test Source","version":"$version","apiVersion":1,
 "minAppVersion":"0.1.0","mediaTypes":["novel"],"capabilities":["search","detail","chapters"],
 "permissions":{"domains":["https://example.com"]},
 "entry":"bundle.js","entrySha256":"${Sha256.hex(bundle)}"}
""".trimIndent().encodeToByteArray()

fun fixturePackage(version: String = "1.0.0"): Pair<ByteArray, ByteArray> {
    val bundle = FIXTURE_BUNDLE.encodeToByteArray()
    return bundle to fixtureManifest(bundle, version)
}
