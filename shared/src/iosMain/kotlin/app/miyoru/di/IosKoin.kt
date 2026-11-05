package app.miyoru.di

import app.miyoru.core.database.IosDatabaseDriverFactory
import app.miyoru.platform.player.PlayerFacade
import app.miyoru.platform.player.createPlayerFacade
import app.miyoru.platform.settings.IosPreferencesSettingsStore
import app.miyoru.plugin.api.zip.CommonZipReader
import app.miyoru.plugin.runtime.RuntimePluginInstaller
import app.miyoru.plugin.runtime.RuntimeSessionProvider
import app.miyoru.plugin.runtime.install.SystemPluginFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import kotlinx.io.files.Path
import org.koin.core.context.startKoin
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** iOS 入口调用：装配全图，返回 PlayerFacade（Swift 侧持有可选）。 */
fun initKoinIos(appVersion: String): PlayerFacade {
    val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String
        ?: throw IllegalStateException("no documents dir")
    val filesRoot = "$docs/miyoru"
    val installFs = SystemPluginFileSystem(Path(filesRoot, "plugins-data"))
    val httpClient = HttpClient(Darwin) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
        install(HttpCookies)
    }
    val koin = startKoin {
        modules(
            appModule(
                driverFactory = IosDatabaseDriverFactory(),
                sessionProvider = RuntimeSessionProvider(installFs),
                installerFacade = RuntimePluginInstaller(
                    installFs, appVersion, false, null,
                    zipReader = CommonZipReader,
                ),
                httpClient = httpClient,
                filesRoot = filesRoot,
                zipReader = CommonZipReader,
                settings = IosPreferencesSettingsStore(),
            ),
        )
    }.koin
    return koin.get()
}
