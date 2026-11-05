package app.miyoru.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import app.miyoru.feature.localimport.ui.ImportScreen
import app.miyoru.feature.library.ui.LibraryScreen
import app.miyoru.feature.novelreader.ui.NovelReaderScreen
import app.miyoru.feature.settings.ui.SettingsScreen
import app.miyoru.feature.site.ui.SiteScreen
import app.miyoru.feature.sources.ui.SourcesScreen
import app.miyoru.feature.statistics.ui.StatisticsScreen
import app.miyoru.feature.storage.ui.StorageScreen
import app.miyoru.feature.videoplayer.ui.PlayerScreen

/** 全应用 NavHost：5 Tab 互跳闭环，子页经回调跳转，返回栈共享。 */
@Composable
fun AppNavHost(nav: NavHostController, padding: PaddingValues, onNavigate: (Any) -> Unit) {
    val openDetail: (MediaKey) -> Unit = { key ->
        nav.navigate(Detail(key.sourceId.value, key.mediaType.name, key.remoteId.value))
    }
    NavHost(nav, startDestination = Home, modifier = Modifier.padding(padding)) {
        composable<Home> {
            HomeScreen(
                onOpenDetail = openDetail,
                onOpenLibrary = { nav.navigate(Library) },
            )
        }
        composable<Library> { LibraryScreen(onOpenDetail = openDetail) }
        composable<History> { HistoryScreen(onOpenDetail = openDetail) }
        composable<Browse> {
            BrowseScreen(
                onOpenSources = { nav.navigate(Sources) },
                onOpenSite = { sourceId -> nav.navigate(Site(sourceId.value)) },
                onOpenExtensions = { nav.navigate(Extensions) },
            )
        }
        composable<More> { MoreScreen(onNavigate) }
        composable<Detail> { e ->
            val r = e.toRoute<Detail>()
            val key = MediaKey(SourceId(r.sourceId), MediaType.valueOf(r.mediaType), RemoteId(r.remoteId))
            DetailScreen(
                key = key,
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
                ComicReaderScreen(key = key, unitId = unitId)
            } else {
                NovelReaderScreen(key = key, unitId = unitId)
            }
        }
        composable<Player> { e ->
            val r = e.toRoute<Player>()
            val key = MediaKey(SourceId(r.sourceId), MediaType.valueOf(r.mediaType), RemoteId(r.remoteId))
            PlayerScreen(key = key, unitId = r.unitId?.let { RemoteId(it) })
        }
        composable<Sources> { SourcesScreen() }
        composable<Site> { e ->
            val r = e.toRoute<Site>()
            SiteScreen(sourceId = SourceId(r.sourceId), onOpenDetail = openDetail)
        }
        composable<Downloads> { DownloadsScreen() }
        composable<Categories> { CategoriesScreen(onOpenLibrary = { nav.navigate(Library) }) }
    composable<Import> { ImportScreen() }
        composable<Statistics> { StatisticsScreen() }
        composable<Extensions> { ExtensionsScreen() }
        composable<Settings> { SettingsScreen() }
        composable<About> { AboutScreen() }
        composable<Storage> { StorageScreen() }
    }
}
