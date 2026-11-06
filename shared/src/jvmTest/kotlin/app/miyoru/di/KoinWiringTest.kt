package app.miyoru.di

import app.miyoru.core.database.InMemoryJvmDriverFactory
import app.miyoru.domain.download.repository.DownloadRepository
import app.miyoru.domain.library.repository.LibraryRepository
import app.miyoru.domain.plugin.repository.PluginRepository
import app.miyoru.domain.source.repository.SourceRepository
import app.miyoru.platform.security.SecretStore
import app.miyoru.plugin.api.InstalledPackage
import app.miyoru.plugin.api.PluginCall
import app.miyoru.plugin.api.PluginInstallerFacade
import app.miyoru.plugin.api.PluginPackageSource
import app.miyoru.plugin.api.PluginResult
import app.miyoru.plugin.api.PluginSession
import app.miyoru.plugin.api.PluginSessionProvider
import io.ktor.client.engine.mock.respondOk
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.Test
import kotlin.test.assertNotNull

private class StubSession : PluginSession {
    override suspend fun invoke(call: PluginCall) = PluginResult(ok = false, errorCode = "STUB")
    override suspend fun close() = Unit
}

private class StubProvider : PluginSessionProvider {
    override suspend fun session(pluginId: String): PluginSession = StubSession()
    override suspend fun release(pluginId: String) = Unit
}

private class StubInstaller : PluginInstallerFacade {
    override suspend fun install(pkg: PluginPackageSource, enforceSignature: Boolean) = throw UnsupportedOperationException()
    override suspend fun rollback(pluginId: String) = Unit
    override suspend fun uninstall(pluginId: String) = Unit
    override fun installDir(pluginId: String) = "plugins/$pluginId"
}

class KoinWiringTest {
    @Test
    fun `core graph resolves`() {
        val koin = startKoin {
            modules(
                appModule(
                    InMemoryJvmDriverFactory(), StubProvider(), StubInstaller(),
                    io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine) {
                        engine {
                            addHandler { respondOk("ok") }
                        }
                    },
                    "build/tmp/koin", app.miyoru.plugin.api.ZipReader { emptyList() },
                    app.miyoru.feature.FakeSettingsRepository(),
                ),
            )
        }.koin
        try {
            assertNotNull(koin.get<LibraryRepository>())
            assertNotNull(koin.get<DownloadRepository>())
            assertNotNull(koin.get<SecretStore>())
            assertNotNull(koin.get<app.miyoru.domain.library.usecase.UpdateProgress>())
            assertNotNull(koin.get<app.miyoru.data.source.SourceSnapshotStore>())
            assertNotNull(koin.get<SourceRepository>())
            assertNotNull(koin.get<PluginRepository>())
            assertNotNull(koin.get<app.miyoru.domain.plugin.repository.PluginCatalogRepository>())
            assertNotNull(koin.get<app.miyoru.domain.source.usecase.SearchAcrossSources>())
            assertNotNull(koin.get<app.miyoru.domain.source.usecase.ResolveContent>())
            assertNotNull(koin.get<app.miyoru.feature.library.presentation.LibraryViewModel>())
            assertNotNull(koin.get<app.miyoru.feature.history.presentation.HistoryViewModel>())
            val key = app.miyoru.core.model.MediaKey(
                app.miyoru.core.model.SourceId("s"),
                app.miyoru.core.model.MediaType.NOVEL,
                app.miyoru.core.model.RemoteId("r"),
            )
            assertNotNull(koin.get<app.miyoru.feature.detail.presentation.DetailViewModel> { org.koin.core.parameter.parametersOf(key) })
            assertNotNull(koin.get<app.miyoru.feature.home.presentation.HomeViewModel>())
            assertNotNull(koin.get<app.miyoru.feature.browse.presentation.BrowseViewModel>())
            assertNotNull(koin.get<app.miyoru.feature.sources.presentation.SourcesViewModel>())
            assertNotNull(koin.get<app.miyoru.feature.site.presentation.SiteViewModel> { org.koin.core.parameter.parametersOf(app.miyoru.core.model.SourceId("s")) })
            assertNotNull(koin.get<app.miyoru.feature.downloads.presentation.DownloadsViewModel>())
            assertNotNull(koin.get<app.miyoru.feature.novelreader.presentation.NovelReaderViewModel> { org.koin.core.parameter.parametersOf(key, null) })
            assertNotNull(koin.get<app.miyoru.feature.comicreader.presentation.ComicReaderViewModel> { org.koin.core.parameter.parametersOf(key, null) })
            assertNotNull(koin.get<app.miyoru.feature.videoplayer.presentation.PlayerViewModel> { org.koin.core.parameter.parametersOf(key, null) })
            assertNotNull(koin.get<app.miyoru.domain.settings.repository.SettingsRepository>())
            assertNotNull(koin.get<app.miyoru.feature.settings.presentation.SettingsViewModel>())
            assertNotNull(koin.get<app.miyoru.domain.cache.repository.CacheRepository>())
            assertNotNull(koin.get<app.miyoru.feature.storage.presentation.StorageViewModel>())
            assertNotNull(koin.get<app.miyoru.feature.statistics.presentation.StatisticsViewModel>())
            assertNotNull(koin.get<app.miyoru.feature.categories.presentation.CategoriesViewModel>())
            assertNotNull(koin.get<app.miyoru.feature.extensions.presentation.ExtensionsViewModel>())
            assertNotNull(koin.get<app.miyoru.feature.repositories.presentation.RepositoriesViewModel>())
            assertNotNull(koin.get<app.miyoru.feature.localimport.presentation.ImportViewModel>())
            assertNotNull(koin.get<app.miyoru.platform.download.DownloadEngine>())
        } finally {
            stopKoin()
        }
        Unit
    }
}
