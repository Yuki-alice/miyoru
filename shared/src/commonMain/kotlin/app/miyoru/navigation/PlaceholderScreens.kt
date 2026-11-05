package app.miyoru.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
private fun StubBody(title: String, lines: List<Pair<String, Any>> = emptyList(), onNavigate: (Any) -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall) }
        items(lines) { (label, route) ->
            ListItem(
                headlineContent = { Text(label) },
                modifier = Modifier.clickable { onNavigate(route) },
            )
        }
    }
}

@Composable fun MoreScreen(onNavigate: (Any) -> Unit) = StubBody(
    "更多",
    listOf(
        "下载" to Downloads, "分类" to Categories, "统计" to Statistics, "插件商店" to Extensions,
        "本地导入" to Import, "设置" to Settings, "关于" to About, "数据与存储" to Storage,
    ),
    onNavigate,
)

@Composable fun PlayerScreen(route: Player) = Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text("播放器", style = MaterialTheme.typography.headlineSmall)
    Text("${route.sourceId} / ${route.remoteId} / ${route.unitId}")
}

@Composable fun CategoriesScreen() = Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text("分类", style = MaterialTheme.typography.headlineSmall)
}

@Composable fun StatisticsScreen() = Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text("统计", style = MaterialTheme.typography.headlineSmall)
}

@Composable fun ExtensionsScreen() = Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text("插件商店", style = MaterialTheme.typography.headlineSmall)
}

@Composable fun SettingsScreen() = Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text("设置", style = MaterialTheme.typography.headlineSmall)
}

@Composable fun AboutScreen() = Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text("关于 miyoru", style = MaterialTheme.typography.headlineSmall)
}

@Composable fun StorageScreen() = Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text("数据与存储", style = MaterialTheme.typography.headlineSmall)
}
