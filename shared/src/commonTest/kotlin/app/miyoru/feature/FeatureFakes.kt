package app.miyoru.feature

import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.domain.download.model.DownloadRequest
import app.miyoru.domain.download.model.DownloadTask
import app.miyoru.domain.download.repository.DownloadRepository
import app.miyoru.domain.local.model.LocalFormat
import app.miyoru.domain.local.model.LocalImportDescriptor
import app.miyoru.domain.local.model.LocalMedia
import app.miyoru.domain.local.repository.LocalImportRepository
import app.miyoru.domain.plugin.model.InstalledPlugin
import app.miyoru.domain.plugin.model.PluginUpdate
import app.miyoru.domain.plugin.repository.PluginRepository
import app.miyoru.domain.source.model.ResolvedContent
import app.miyoru.domain.source.model.SourceInfo
import app.miyoru.platform.download.DownloadEngine
import app.miyoru.plugin.api.ZipReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class FakePluginRepository : PluginRepository {
    val installed = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val enabledCalls = mutableListOf<Pair<String, Boolean>>()
    val uninstalled = mutableListOf<String>()
    override fun observeInstalled(): Flow<List<InstalledPlugin>> = installed
    override suspend fun setEnabled(pluginId: String, enabled: Boolean) {
        enabledCalls.add(pluginId to enabled)
        installed.value = installed.value.map {
            if (it.id == pluginId) it.copy(enabled = enabled) else it
        }
    }
    override suspend fun checkForUpdate(pluginId: String): PluginUpdate? = null
    override suspend fun rollback(pluginId: String): InstalledPlugin =
        installed.value.first { it.id == pluginId }
    override suspend fun uninstall(pluginId: String) {
        uninstalled.add(pluginId)
        installed.value = installed.value.filterNot { it.id == pluginId }
    }
}

class FakeDownloadRepository : DownloadRepository {
    val tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val calls = mutableListOf<String>()
    override fun observeTasks(): Flow<List<DownloadTask>> = tasks
    override suspend fun enqueue(request: DownloadRequest): String {
        val id = "t${tasks.value.size}"
        tasks.value = tasks.value + DownloadTask(id, "QUEUED")
        calls.add("enqueue:$id")
        return id
    }
    override suspend fun pause(taskId: String) {
        calls.add("pause:$taskId")
        tasks.value = tasks.value.map { if (it.id == taskId) it.copy(state = "PAUSED") else it }
    }
    override suspend fun resume(taskId: String) {
        calls.add("resume:$taskId")
        tasks.value = tasks.value.map { if (it.id == taskId) it.copy(state = "QUEUED") else it }
    }
    override suspend fun cancel(taskId: String) {
        calls.add("cancel:$taskId")
        tasks.value = tasks.value.map { if (it.id == taskId) it.copy(state = "CANCELED") else it }
    }
    override suspend fun remove(taskId: String, deleteFiles: Boolean) {
        calls.add("remove:$taskId")
        tasks.value = tasks.value.filterNot { it.id == taskId }
    }
}

class FakeDownloadEngine : DownloadEngine {
    val started = mutableListOf<String>()
    override fun enqueue(taskId: String): Flow<app.miyoru.platform.download.DownloadEvent> = flow {
        started.add(taskId)
        emit(app.miyoru.platform.download.DownloadEvent(taskId, "COMPLETED"))
    }
    override suspend fun pause(taskId: String) = Unit
    override suspend fun resume(taskId: String) = Unit
    override suspend fun cancel(taskId: String) = Unit
}

class FakeLocalImportRepository : LocalImportRepository {
    val media = MutableStateFlow<List<LocalMedia>>(emptyList())
    val removed = mutableListOf<MediaKey>()
    override fun observeLocalMedia(): Flow<List<LocalMedia>> = media
    override suspend fun import(descriptor: LocalImportDescriptor): LocalMedia {
        val m = LocalMedia(
            MediaKey(SourceId("local"), MediaType.NOVEL, RemoteId(descriptor.relativePath)),
            descriptor.fileName, descriptor.format, 1,
        )
        media.value = media.value + m
        return m
    }
    override suspend fun remove(key: MediaKey) {
        removed.add(key)
        media.value = media.value.filterNot { it.key == key }
    }
}

class FakeZipReader(val entries: List<app.miyoru.plugin.api.PackageEntry> = emptyList()) : ZipReader {
    override suspend fun read(zipBytes: ByteArray) = entries
}

fun sourceInfo(id: String) = SourceInfo(SourceId(id), id, listOf(MediaType.NOVEL), listOf("search"), true)

fun novelMedia(source: String, remote: String, title: String) = media(source, MediaType.NOVEL, remote, title)

class FakeCacheRepository : app.miyoru.domain.cache.repository.CacheRepository {
    val bytes = kotlinx.coroutines.flow.MutableStateFlow(0L)
    var cleared = 0
    override fun observeSizeBytes(): kotlinx.coroutines.flow.Flow<Long> = bytes
    override suspend fun clear() {
        cleared++
        bytes.value = 0L
    }
}

class FakeSettingsRepository : app.miyoru.domain.settings.repository.SettingsRepository {
    val theme = kotlinx.coroutines.flow.MutableStateFlow("system")
    val defaultType = kotlinx.coroutines.flow.MutableStateFlow("NOVEL")
    val concurrency = kotlinx.coroutines.flow.MutableStateFlow(3)
    override fun observeThemeMode() = theme
    override suspend fun setThemeMode(mode: String) { theme.value = mode }
    override fun observeDefaultMediaType() = defaultType
    override suspend fun setDefaultMediaType(type: String) { defaultType.value = type }
    override fun observeConcurrency() = concurrency
    override suspend fun setConcurrency(n: Int) { concurrency.value = n.coerceIn(1, 8) }
}
