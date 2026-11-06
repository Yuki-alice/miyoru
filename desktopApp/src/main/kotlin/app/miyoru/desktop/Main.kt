package app.miyoru.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.miyoru.App
import app.miyoru.core.database.JvmDatabaseDriverFactory
import app.miyoru.di.appModule
import app.miyoru.plugin.runtime.RuntimePluginInstaller
import app.miyoru.plugin.runtime.RuntimeSessionProvider
import app.miyoru.plugin.runtime.install.SystemPluginFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import kotlinx.io.files.Path
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import org.koin.core.context.startKoin
import java.io.File

fun main() {
    val appDir = File(System.getProperty("user.home"), ".miyoru").apply { mkdirs() }
    val filesRoot = appDir.path
    val installFs = SystemPluginFileSystem(Path(filesRoot, "plugins-data"))
    val settingsStore = PreferenceDataStoreFactory.create(
        produceFile = { appDir.resolve("settings.preferences_pb") },
    )
    val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
        install(HttpCookies)
    }
    val demo = System.getenv("MIYORU_DEMO") == "1" || System.getProperty("miyoru.demo") == "true"
    startKoin {
        // 演示数据需覆盖正式仓储绑定（仅 MIYORU_DEMO=1 时注入）
        if (demo) allowOverride(true)
        modules(
            listOf(
                appModule(
                    driverFactory = JvmDatabaseDriverFactory(appDir.resolve("db").apply { mkdirs() }.path),
                    sessionProvider = RuntimeSessionProvider(installFs),
                    installerFacade = RuntimePluginInstaller(
                        installFs, "0.1.0", false, null,
                        zipReader = app.miyoru.plugin.api.zip.CommonZipReader,
                    ),
                    httpClient = httpClient,
                    filesRoot = filesRoot,
                    settings = app.miyoru.data.settings.SettingsRepositoryImpl(settingsStore),
                    zipReader = app.miyoru.plugin.api.zip.CommonZipReader,
                ),
            ) + if (demo) app.miyoru.dev.DemoContent.modules() else emptyList(),
        )
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "miyoru",
            // 设计原型宽屏画板 1280×800，桌面端按此尺寸 1:1 对照
            state = rememberWindowState(size = DpSize(1280.dp, 800.dp)),
            // 对照模式置顶：逐屏比对时不被编辑器遮挡（仅开发用，正常启动不受影响）
            alwaysOnTop = demo,
        ) {
            App()
        }
    }
}
