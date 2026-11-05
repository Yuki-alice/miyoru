package app.miyoru.domain.settings.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeThemeMode(): Flow<String>
    suspend fun setThemeMode(mode: String)
    fun observeDefaultMediaType(): Flow<String>
    suspend fun setDefaultMediaType(type: String)
    fun observeConcurrency(): Flow<Int>
    suspend fun setConcurrency(n: Int)

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
