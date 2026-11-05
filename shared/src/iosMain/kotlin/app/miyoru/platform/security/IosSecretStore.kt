package app.miyoru.platform.security

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import platform.CoreFoundation.CFBooleanRef
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS Keychain（Security.framework，全 C-API 组装查询字典，无 ObjC 桥接）。
 * service = app.miyoru，account = alias。
 */
@OptIn(ExperimentalForeignApi::class)
class IosKeychainSecretStore : SecretStore {
    private fun cfString(s: String): CFStringRef? =
        CFStringCreateWithCString(kCFAllocatorDefault, s.cstr, kCFStringEncodingUTF8)

    private fun MemScope.query(account: String, value: CFTypeRef?, returnData: Boolean): CFDictionaryRef {
        val keys = mutableListOf<CFStringRef?>()
        val values = mutableListOf<CFTypeRef?>()
        fun add(k: CFStringRef?, v: CFTypeRef?) {
            keys.add(k)
            values.add(v)
        }
        add(kSecClass, kSecClassGenericPassword?.reinterpret())
        add(kSecAttrService, cfString("app.miyoru"))
        add(kSecAttrAccount, cfString(account))
        if (value != null) add(kSecValueData, value)
        if (returnData) add(kSecReturnData, kCFBooleanTrue)
        val n = keys.size
        val keyArray = allocArray<COpaquePointerVar>(n)
        val valArray = allocArray<COpaquePointerVar>(n)
        for (i in 0 until n) {
            keyArray[i] = keys[i]?.reinterpret()
            valArray[i] = values[i]?.reinterpret()
        }
        return CFDictionaryCreate(kCFAllocatorDefault, keyArray, valArray, n.toLong(), null, null)!!
    }

    private fun ByteArray.toCFData(): CFDataRef? = memScoped {
        if (isEmpty()) return CFDataCreate(kCFAllocatorDefault, null, 0)
        val native = allocArray<kotlinx.cinterop.UByteVar>(size)
        for (i in indices) native[i] = this@toCFData[i].toUByte()
        CFDataCreate(kCFAllocatorDefault, native, size.toLong())
    }

    private fun CFDataRef.toByteArray(): ByteArray = memScoped {
        val len = CFDataGetLength(this@toByteArray)?.toInt() ?: 0
        if (len == 0) return ByteArray(0)
        val out = ByteArray(len)
        val src = CFDataGetBytePtr(this@toByteArray)!!.reinterpret<ByteVar>()
        for (i in 0 until len) out[i] = src[i]
        out
    }

    override suspend fun put(alias: String, value: ByteArray) = memScoped {
        SecItemDelete(query(alias, null, false))
        val data = value.toCFData()
        val status = SecItemAdd(query(alias, data, false), null)
        if (status != errSecSuccess) throw IllegalStateException("keychain add failed: $status")
    }

    override suspend fun get(alias: String): ByteArray? = memScoped {
        val result = alloc<COpaquePointerVar>()
        val status = SecItemCopyMatching(query(alias, null, true), result.ptr)
        if (status != errSecSuccess) return null
        val raw: COpaquePointer = result.value ?: return null
        val data: CFDataRef = raw.reinterpret()
        data.toByteArray()
    }

    override suspend fun delete(alias: String) {
        memScoped { SecItemDelete(query(alias, null, false)) }
    }
}
