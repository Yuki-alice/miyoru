package app.miyoru.platform.settings

import app.miyoru.domain.settings.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import platform.Foundation.NSUserDefaults

/**
 * iOS 设置存储（NSUserDefaults suitesharing，真实持久化；DataStore 的 okio 路径
 * 在 common 侧不可构造，故 iOS 用平台实现，接口保持一致）。
 */
class IosPreferencesSettingsStore : SettingsRepository {
    private val defaults = NSUserDefaults.standardUserDefaults()
    private val theme = MutableStateFlow(currentTheme())
    private val defaultType = MutableStateFlow(currentType())
    private val concurrency = MutableStateFlow(currentConcurrency())

    private fun currentTheme(): String =
        (defaults.stringForKey(KEY_THEME) as String?).takeIf { it in THEMES }
            ?: SettingsRepository.THEME_SYSTEM
    private fun currentType(): String = (defaults.stringForKey(KEY_TYPE) as String?) ?: "NOVEL"
    private fun currentConcurrency(): Int {
        val v = defaults.integerForKey(KEY_CONCURRENCY)
        return if (v in 1..8) v.toInt() else 3
    }

    override fun observeThemeMode(): Flow<String> = theme
    override suspend fun setThemeMode(mode: String) {
        require(mode in THEMES)
        defaults.setObject(mode, KEY_THEME)
        theme.value = mode
    }

    override fun observeDefaultMediaType(): Flow<String> = defaultType
    override suspend fun setDefaultMediaType(type: String) {
        defaults.setObject(type, KEY_TYPE)
        defaultType.value = type
    }

    override suspend fun setConcurrency(n: Int) {
        val v = n.coerceIn(1, 8)
        defaults.setInteger(v.toLong(), KEY_CONCURRENCY)
        concurrency.value = v
    }

    override fun observeConcurrency(): Flow<Int> = concurrency

    companion object {
        private const val KEY_THEME = "miyoru.theme"
        private const val KEY_TYPE = "miyoru.defaultType"
        private const val KEY_CONCURRENCY = "miyoru.concurrency"
        private val THEMES = setOf(
            SettingsRepository.THEME_SYSTEM,
            SettingsRepository.THEME_LIGHT,
            SettingsRepository.THEME_DARK,
        )
    }
}
