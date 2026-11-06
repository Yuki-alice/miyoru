package app.miyoru.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.feature.about.ui.AboutScreen
import app.miyoru.feature.browse.ui.BrowseScreen
import app.miyoru.feature.categories.ui.CategoriesScreen
import app.miyoru.feature.comicreader.ui.ComicReaderScreen
import app.miyoru.feature.detail.ui.DetailScreen
import app.miyoru.feature.downloads.ui.DownloadsScreen
import app.miyoru.feature.extensions.ui.ExtensionsScreen
import app.miyoru.feature.history.ui.HistoryScreen
import app.miyoru.feature.home.ui.HomeScreen
import app.miyoru.feature.library.ui.LibraryScreen
import app.miyoru.feature.localimport.ui.ImportScreen
import app.miyoru.feature.more.ui.MoreScreen
import app.miyoru.feature.novelreader.ui.NovelReaderScreen
import app.miyoru.feature.repositories.ui.RepositoriesScreen
import app.miyoru.feature.settings.ui.SettingsScreen
import app.miyoru.feature.site.ui.SiteScreen
import app.miyoru.feature.sources.ui.SourcesScreen
import app.miyoru.feature.statistics.ui.StatisticsScreen
import app.miyoru.feature.storage.ui.StorageScreen
import app.miyoru.feature.videoplayer.ui.PlayerScreen

/** 全应用 NavHost：5 Tab 互跳闭环，子页经回调跳转，返回栈共享。 */
@Composable
fun AppNavHost(nav: NavHostController, wide: Boolean, onNavigate: (Any) -> Unit) {
    val openDetail: (MediaKey) -> Unit = { key ->
        nav.navigate(Detail(key.sourceId.value, key.mediaType.name, key.remoteId.value))
    }
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = Home) {
        composable<Home> {
            HomeScreen(
                wide = wide,
                onOpenDetail = openDetail,
                onOpenLibrary = { nav.navigate(Library) },
            )
        }
        composable<Library> { LibraryScreen(wide = wide, onOpenDetail = openDetail) }
        composable<History> { HistoryScreen(wide = wide, onOpenDetail = openDetail) }
        composable<Browse> {
            BrowseScreen(
                wide = wide,
                onOpenSources = { nav.navigate(Sources) },
                onOpenSite = { sourceId -> nav.navigate(Site(sourceId.value)) },
                onOpenExtensions = { nav.navigate(Extensions) },
                onOpenRepositories = { nav.navigate(Repositories) },
            )
        }
        composable<More> { MoreScreen(wide = wide, onNavigate = onNavigate) }
        composable<Detail> { e ->
            val r = e.toRoute<Detail>()
            val key = MediaKey(SourceId(r.sourceId), MediaType.valueOf(r.mediaType), RemoteId(r.remoteId))
            DetailScreen(
                wide = wide,
                key = key,
                onBack = back,
                onOpenReader = { unitId ->
                    if (key.mediaType == MediaType.VIDEO) {
                        nav.navigate(Player(r.sourceId, r.mediaType, r.remoteId, unitId?.value))
                    } else {
                        nav.navigate(Reader(r.sourceId, r.mediaType, r.remoteId, unitId?.value))
                    }
                },
            )
        }
        composable<Reader> { e ->
            val r = e.toRoute<Reader>()
            val key = MediaKey(SourceId(r.sourceId), MediaType.valueOf(r.mediaType), RemoteId(r.remoteId))
            val unitId = r.unitId?.let { RemoteId(it) }
            if (key.mediaType == MediaType.COMIC) {
                ComicReaderScreen(wide = wide, key = key, unitId = unitId, onBack = back)
            } else {
                NovelReaderScreen(wide = wide, key = key, unitId = unitId, onBack = back)
            }
        }
        composable<Player> { e ->
            val r = e.toRoute<Player>()
            val key = MediaKey(SourceId(r.sourceId), MediaType.valueOf(r.mediaType), RemoteId(r.remoteId))
            PlayerScreen(wide = wide, key = key, unitId = r.unitId?.let { RemoteId(it) }, onBack = back)
        }
        composable<Sources> {
            SourcesScreen(
                wide = wide,
                onBack = back,
                onOpenExtensions = { nav.navigate(Extensions) },
                onOpenRepositories = { nav.navigate(Repositories) },
            )
        }
        composable<Site> { e ->
            val r = e.toRoute<Site>()
            SiteScreen(
                wide = wide,
                sourceId = SourceId(r.sourceId),
                onBack = back,
                onOpenDetail = openDetail,
            )
        }
        composable<Downloads> { DownloadsScreen(wide = wide, onBack = back) }
        composable<Categories> {
            CategoriesScreen(wide = wide, onBack = back, onOpenLibrary = { nav.navigate(Library) })
        }
        composable<Import> { ImportScreen(wide = wide, onBack = back) }
        composable<Statistics> { StatisticsScreen(wide = wide, onBack = back) }
        composable<Extensions> {
            ExtensionsScreen(
                wide = wide,
                onBack = back,
                onOpenSources = { nav.navigate(Sources) },
                onOpenRepositories = { nav.navigate(Repositories) },
            )
        }
        composable<Repositories> {
            RepositoriesScreen(
                wide = wide,
                onBack = back,
                onOpenExtensions = { nav.navigate(Extensions) },
            )
        }
        composable<Settings> { SettingsScreen(wide = wide, onBack = back) }
        composable<About> {
            AboutScreen(wide = wide, onBack = back, onOpenStorage = { nav.navigate(Storage) })
        }
        composable<Storage> { StorageScreen(wide = wide, onBack = back) }
    }
}
