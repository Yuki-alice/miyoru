package app.miyoru.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute

private val TAB_ORDER: List<Pair<Any, kotlin.reflect.KClass<*>>> = listOf(
    Home to Home::class,
    Library to Library::class,
    History to History::class,
    Browse to Browse::class,
    More to More::class,
)

/** 自适应骨架：窄屏底栏 / 宽屏（≥840dp）侧栏仅 5 主 Tab 显示；子页沉浸 + 顶栏返回。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold() {
    val nav: NavHostController = rememberNavController()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val entry by nav.currentBackStackEntryAsState()
        val dest = entry?.destination
        val currentTab = TAB_ORDER.firstOrNull { (_, k) ->
            dest?.hierarchy?.any { it.hasRoute(k) } == true
        }?.first
        val onTab: (Any) -> Unit = { route ->
            nav.navigate(route) {
                popUpTo(nav.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        Scaffold(
            topBar = {
                if (currentTab == null) {
                    val e = entry
                    val label = if (e == null) "" else try {
                        val r: Any = when {
                            e.destination.hasRoute(Detail::class) -> e.toRoute<Detail>()
                            e.destination.hasRoute(Reader::class) -> e.toRoute<Reader>()
                            e.destination.hasRoute(Player::class) -> e.toRoute<Player>()
                            e.destination.hasRoute(Site::class) -> e.toRoute<Site>()
                            e.destination.hasRoute(Sources::class) -> Sources
                            e.destination.hasRoute(Downloads::class) -> Downloads
                            e.destination.hasRoute(Categories::class) -> Categories
                            e.destination.hasRoute(Statistics::class) -> Statistics
                            e.destination.hasRoute(Extensions::class) -> Extensions
                            e.destination.hasRoute(Settings::class) -> Settings
                            e.destination.hasRoute(About::class) -> About
                            e.destination.hasRoute(Import::class) -> Import
                            else -> Storage
                        }
                        routeLabel(r)
                    } catch (_: Exception) {
                        ""
                    }
                    TopAppBar(
                        title = { Text(label) },
                        navigationIcon = {
                            TextButton(onClick = { nav.popBackStack() }) { Text("‹ 返回") }
                        },
                    )
                }
            },
            bottomBar = {
                if (!wide && currentTab != null) {
                    NavigationBar {
                        TAB_ORDER.forEach { (route, k) ->
                            val selected = dest?.hierarchy?.any { it.hasRoute(k) } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { if (!selected) onTab(route) },
                                icon = {},
                                label = { Text(MAIN_TAB_LABELS.getValue(route)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize()) {
                if (wide && currentTab != null) {
                    NavigationRail {
                        TAB_ORDER.forEach { (route, k) ->
                            val selected = dest?.hierarchy?.any { it.hasRoute(k) } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { if (!selected) onTab(route) },
                                icon = {},
                                label = { Text(MAIN_TAB_LABELS.getValue(route)) },
                            )
                        }
                    }
                }
                AppNavHost(nav, padding, onTab)
            }
        }
    }
}
