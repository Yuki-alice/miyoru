package app.miyoru.domain.plugin.repository

import app.miyoru.domain.plugin.model.InstalledPlugin
import app.miyoru.domain.plugin.model.PluginUpdate
import kotlinx.coroutines.flow.Flow

interface PluginRepository {
    fun observeInstalled(): Flow<List<InstalledPlugin>>
    suspend fun setEnabled(pluginId: String, enabled: Boolean)
    suspend fun checkForUpdate(pluginId: String): PluginUpdate?
    suspend fun rollback(pluginId: String): InstalledPlugin
    suspend fun uninstall(pluginId: String)
}
