package app.miyoru.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.miyoru.core.ui.components.MiyoruBottomBar
import app.miyoru.core.ui.components.MiyoruNavRail
import app.miyoru.core.ui.components.NavItem
import app.miyoru.core.ui.icon.MiyoruIcons

internal val TAB_ORDER: List<Pair<Any, kotlin.reflect.KClass<*>>> = listOf(
    Home to Home::class,
    Library to Library::class,
    History to History::class,
    Browse to Browse::class,
    More to More::class,
)

private fun tabIcon(route: Any) = when (route) {
    is Home -> MiyoruIcons.Home
    is Library -> MiyoruIcons.Library
    is History -> MiyoruIcons.History
    is Browse -> MiyoruIcons.Browse
    else -> MiyoruIcons.More
}

/**
 * 自适应骨架：宽屏（≥840dp）显示 80dp 侧栏且仅 5 个主 Tab 有导航；
 * 窄屏显示底栏；子页两端都沉浸，由各屏自己的顶栏提供返回。
 */
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
        val items = TAB_ORDER.map { (route, _) -> NavItem(MAIN_TAB_LABELS.getValue(route), tabIcon(route)) }
        val index = TAB_ORDER.indexOfFirst { it.first == currentTab }
        val select: (Int) -> Unit = { onTab(TAB_ORDER[it].first) }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                if (currentTab != null) {
                    MiyoruNavRail(items = items, selected = index, onSelect = select)
                }
                Box(Modifier.weight(1f)) {
                    AppNavHost(nav = nav, wide = true, onNavigate = onTab)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    AppNavHost(nav = nav, wide = false, onNavigate = onTab)
                }
                if (currentTab != null) {
                    MiyoruBottomBar(items = items, selected = index, onSelect = select)
                }
            }
        }
    }
}
