package app.miyoru.platform.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore 实现（AES/GCM，API 33+ Ed25519 同理可用；minSdk 24，KeyGen 需 23+）。
 * 密文存私有 SharedPreferences，密钥不出 Keystore。
 */
class AndroidKeystoreSecretStore(context: Context) : SecretStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("miyoru_secrets", Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getKey(alias: String): SecretKey {
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGen.generateKey()
    }

    override suspend fun put(alias: String, value: ByteArray) {
        if (Build.VERSION.SDK_INT < 23) throw UnsupportedOperationException("keystore requires API 23+")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey(alias))
        val iv = cipher.iv
        val cipherText = cipher.doFinal(value)
        prefs.edit()
            .putString(alias + IV_SUFFIX, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(alias, Base64.encodeToString(cipherText, Base64.NO_WRAP))
            .apply()
    }

    override suspend fun get(alias: String): ByteArray? {
        if (Build.VERSION.SDK_INT < 23) throw UnsupportedOperationException("keystore requires API 23+")
        val ivB64 = prefs.getString(alias + IV_SUFFIX, null) ?: return null
        val ctB64 = prefs.getString(alias, null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, getKey(alias),
                GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(ctB64, Base64.NO_WRAP))
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun delete(alias: String) {
        prefs.edit().remove(alias).remove(alias + IV_SUFFIX).apply()
        runCatching { keyStore.deleteEntry(alias) }
    }

    companion object {
        private const val IV_SUFFIX = "_iv"
    }
}
