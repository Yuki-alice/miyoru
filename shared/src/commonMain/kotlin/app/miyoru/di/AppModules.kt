package app.miyoru.di

import app.miyoru.core.common.AppDispatchers
import app.miyoru.core.common.DefaultAppDispatchers
import app.miyoru.core.database.DatabaseDriverFactory
import app.miyoru.data.download.DownloadRepositoryImpl
import app.miyoru.data.library.LibraryRepositoryImpl
import app.miyoru.data.plugin.PluginRepositoryImpl
import app.miyoru.data.source.SourceRepositoryImpl
import app.miyoru.data.source.SourceSnapshotStore
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.download.repository.DownloadRepository
import app.miyoru.domain.library.repository.LibraryRepository
import app.miyoru.domain.library.usecase.UpdateProgress
import app.miyoru.domain.source.usecase.ResolveContent
import app.miyoru.domain.source.usecase.SearchAcrossSources
import app.miyoru.data.download.DownloadUrlResolver
import app.miyoru.data.download.HttpDownloadEngine
import app.miyoru.data.download.PluginDownloadSource
import app.miyoru.data.local.LocalImportRepositoryImpl
import app.miyoru.domain.local.repository.LocalImportRepository
import app.miyoru.domain.local.usecase.ImportLocalFile
import app.miyoru.platform.download.DownloadEngine
import app.miyoru.platform.player.PlayerFacade
import app.miyoru.platform.player.createPlayerFacade
import app.miyoru.plugin.api.ZipReader
import io.ktor.client.HttpClient
import app.miyoru.domain.plugin.repository.PluginRepository
import app.miyoru.domain.source.repository.SourceRepository
import app.miyoru.plugin.api.PluginInstallerFacade
import app.miyoru.plugin.api.PluginSessionProvider
import app.miyoru.platform.security.InMemorySecretStore
import app.miyoru.platform.security.SecretStore
import app.miyoru.feature.browse.presentation.BrowseViewModel
import app.miyoru.feature.detail.presentation.DetailViewModel
import app.miyoru.data.cache.CacheRepositoryImpl
import app.miyoru.data.plugin.InstallPluginFromUrl

import app.miyoru.domain.cache.repository.CacheRepository
import app.miyoru.domain.settings.repository.SettingsRepository
import app.miyoru.feature.categories.presentation.CategoriesViewModel
import app.miyoru.feature.comicreader.presentation.ComicReaderViewModel
import app.miyoru.feature.downloads.presentation.DownloadsViewModel
import app.miyoru.feature.extensions.presentation.ExtensionsViewModel
import app.miyoru.feature.settings.presentation.SettingsViewModel
import app.miyoru.feature.site.presentation.SiteViewModel
import app.miyoru.feature.sources.presentation.SourcesViewModel
import app.miyoru.feature.statistics.presentation.StatisticsViewModel
import app.miyoru.feature.storage.presentation.StorageViewModel
import app.miyoru.feature.videoplayer.presentation.PlayerViewModel
import app.miyoru.feature.history.presentation.HistoryViewModel
import app.miyoru.feature.home.presentation.HomeViewModel
import app.miyoru.feature.localimport.presentation.ImportViewModel
import app.miyoru.feature.library.presentation.LibraryViewModel
import app.miyoru.feature.novelreader.presentation.NovelReaderViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * DI 组合根：平台只传入 DatabaseDriverFactory，其余装配收敛在这里。
 * SourceRepository / PluginRepository 已落地（B4）；LocalImportRepository 在 B5 落地后接入。
 */
fun appModule(
    driverFactory: DatabaseDriverFactory,
    sessionProvider: PluginSessionProvider,
    installerFacade: PluginInstallerFacade,
    httpClient: HttpClient,
    filesRoot: String,
    zipReader: ZipReader,
    settings: SettingsRepository,
): Module = module {
    single { driverFactory.createDriver() }
    single { MiyoruDatabase(get()) }
    single<AppDispatchers> { DefaultAppDispatchers() }
    single<LibraryRepository> { LibraryRepositoryImpl(get()) }
    single<DownloadRepository> { DownloadRepositoryImpl(get()) }
    single<SecretStore> { InMemorySecretStore() }
    single { SourceSnapshotStore(get()) }
    single { UpdateProgress(get()) }
    single<SourceRepository> { SourceRepositoryImpl(get(), sessionProvider, get()) }
    single<PluginRepository> { PluginRepositoryImpl(get(), installerFacade) }
    single { zipReader }
    single { ImportLocalFile(get()) }
    single { SearchAcrossSources(get()) }
    single { ResolveContent(get()) }
    single { httpClient }
    single<DownloadUrlResolver> { PluginDownloadSource(get(), get(), get()) }
    single<DownloadEngine> { HttpDownloadEngine(get(), httpClient, get(), "$filesRoot/downloads") }
    single<PlayerFacade> { createPlayerFacade() }
    single<LocalImportRepository> { LocalImportRepositoryImpl(get(), zipReader, "$filesRoot/inbox", "$filesRoot/library") }
    single(named("filesRoot")) { filesRoot }
    viewModel { LibraryViewModel(get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { (key: app.miyoru.core.model.MediaKey) -> DetailViewModel(key, get(), get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { BrowseViewModel(get()) }
    viewModel { SourcesViewModel(get()) }
    viewModel { (sourceId: app.miyoru.core.model.SourceId) -> SiteViewModel(sourceId, get()) }
    viewModel { DownloadsViewModel(get(), get()) }
    viewModel { (key: app.miyoru.core.model.MediaKey, unitId: app.miyoru.core.model.RemoteId?) ->
        NovelReaderViewModel(key, unitId, get(), get(), get(), get(), get(named("filesRoot")), get()) }
    viewModel { (key: app.miyoru.core.model.MediaKey, unitId: app.miyoru.core.model.RemoteId?) ->
        ComicReaderViewModel(key, unitId, get(), get(), get(), get(), get(named("filesRoot")), get()) }
    viewModel { (key: app.miyoru.core.model.MediaKey, unitId: app.miyoru.core.model.RemoteId?) ->
        PlayerViewModel(key, unitId, get(), get(), get()) }
    single<SettingsRepository> { settings }
    viewModel { SettingsViewModel(get()) }
    single<CacheRepository> { CacheRepositoryImpl(get()) }
    viewModel { StorageViewModel(get()) }
    viewModel { StatisticsViewModel(get(), get()) }
    viewModel { CategoriesViewModel(get()) }
    viewModel { ImportViewModel(get(named("filesRoot")), get(), get()) }
    viewModel { ExtensionsViewModel(InstallPluginFromUrl(httpClient, installerFacade)) }
}
