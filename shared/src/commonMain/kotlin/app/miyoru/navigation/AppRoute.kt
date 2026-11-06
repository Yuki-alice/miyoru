package app.miyoru.navigation

import kotlinx.serialization.Serializable

// —— 主 Tab（5 个，宽屏显示侧边 rail） ——
@Serializable data object Home
@Serializable data object Library
@Serializable data object History
@Serializable data object Browse
@Serializable data object More

// —— 内容消费（沉浸页，不显示 rail/bottomBar） ——
@Serializable data class Detail(val sourceId: String, val mediaType: String, val remoteId: String)
@Serializable data class Reader(val sourceId: String, val mediaType: String, val remoteId: String, val unitId: String? = null)
@Serializable data class Player(val sourceId: String, val mediaType: String, val remoteId: String, val unitId: String? = null)

// —— 浏览与来源 ——
@Serializable data object Sources
@Serializable data class Site(val sourceId: String)

@Serializable data object Import

// —— 更多 · 7 子页 ——
@Serializable data object Downloads
@Serializable data object Categories
@Serializable data object Statistics
@Serializable data object Extensions
@Serializable data object Repositories
@Serializable data object Settings
@Serializable data object About
@Serializable data object Storage

/** 宽屏显示 rail 的 5 个主 Tab（原型规则：仅 MAIN 显示侧栏，其余沉浸）。 */
val MAIN_TABS: List<Any> = listOf(Home, Library, History, Browse, More)

val MAIN_TAB_LABELS: Map<Any, String> = mapOf(
    Home to "首页", Library to "书架", History to "历史", Browse to "浏览", More to "更多",
)

fun routeLabel(route: Any): String = when (route) {
    is Home -> "首页"
    is Library -> "书架"
    is History -> "历史"
    is Browse -> "浏览"
    is More -> "更多"
    is Detail -> "作品详情"
    is Reader -> "阅读"
    is Player -> "播放"
    is Sources -> "数据源管理"
    is Site -> "站点浏览"
    is Downloads -> "下载"
    is Categories -> "分类"
    is Statistics -> "统计"
    is Extensions -> "插件商店"
    is Repositories -> "插件仓库"
    is Settings -> "设置"
    is About -> "关于"
    is Storage -> "数据与存储"
    is Import -> "本地导入"
    else -> ""
}
