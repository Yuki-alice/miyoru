package app.miyoru.platform.security

import java.util.Base64
import java.util.prefs.Preferences

/**
 * 桌面 JVM 实现（java.util.prefs，OS 用户级存储，非硬件密钥库——安全等级低于移动端，
 * 高敏感场景提示用户，这是当前桌面平台的诚实上限）。
 */
class JvmSecretStore : SecretStore {
    private val prefs: Preferences = Preferences.userRoot().node("app/miyoru/secrets")

    override suspend fun put(alias: String, value: ByteArray) {
        prefs.put(alias, Base64.getEncoder().encodeToString(value))
        prefs.flush()
    }

    override suspend fun get(alias: String): ByteArray? {
        val s = prefs.get(alias, null) ?: return null
        return runCatching { Base64.getDecoder().decode(s) }.getOrNull()
    }

    override suspend fun delete(alias: String) {
        prefs.remove(alias)
        prefs.flush()
    }
}
