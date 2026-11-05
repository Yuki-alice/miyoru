package app.miyoru.feature.about.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("关于", style = MaterialTheme.typography.headlineSmall)
        Text("miyoru 0.1.0（M0 工程骨架）", style = MaterialTheme.typography.titleMedium)
        Text("视频 · 小说 · 漫画三合一媒体库。应用自身不内置、分发或托管任何内容源；插件机制见项目文档。")
        Text("第三方组件：Kotlin Multiplatform、Compose Multiplatform、SQLDelight、Ktor、Koin、Coil、Ksoup、QuickJS-KT。")
    }
}
