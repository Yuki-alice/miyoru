package app.miyoru.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.miyoru.domain.settings.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(private val store: DataStore<Preferences>) : SettingsRepository {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DEFAULT_TYPE = stringPreferencesKey("default_media_type")
        val CONCURRENCY = intPreferencesKey("download_concurrency")
    }

    override fun observeThemeMode(): Flow<String> =
        store.data.map { it[Keys.THEME] ?: SettingsRepository.THEME_SYSTEM }

    override suspend fun setThemeMode(mode: String) {
        require(mode in setOf(SettingsRepository.THEME_SYSTEM, SettingsRepository.THEME_LIGHT, SettingsRepository.THEME_DARK))
        store.edit { it[Keys.THEME] = mode }
    }

    override fun observeDefaultMediaType(): Flow<String> =
        store.data.map { it[Keys.DEFAULT_TYPE] ?: "NOVEL" }

    override suspend fun setDefaultMediaType(type: String) {
        store.edit { it[Keys.DEFAULT_TYPE] = type }
    }

    override fun observeConcurrency(): Flow<Int> =
        store.data.map { (it[Keys.CONCURRENCY] ?: 3).coerceIn(1, 8) }

    override suspend fun setConcurrency(n: Int) {
        store.edit { it[Keys.CONCURRENCY] = n.coerceIn(1, 8) }
    }
}
