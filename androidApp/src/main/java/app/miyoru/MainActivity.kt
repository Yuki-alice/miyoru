package app.miyoru

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.miyoru.core.database.AndroidDatabaseDriverFactory
import app.miyoru.di.appModule
import app.miyoru.platform.player.AndroidAppContext
import app.miyoru.plugin.runtime.PluginEngineConfig
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAppContext.app = applicationContext
        val filesRoot = Path(filesDir.path, "miyoru").toString()
        val installFs = SystemPluginFileSystem(Path(filesRoot, "plugins-data"))
        val settingsStore = PreferenceDataStoreFactory.create(
            produceFile = { applicationContext.filesDir.resolve("miyoru/settings.preferences_pb") },
        )
        val httpClient = HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
            install(HttpCookies)
        }
        startKoin {
            modules(
                appModule(
                    driverFactory = AndroidDatabaseDriverFactory(applicationContext),
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
            )
        }
        setContent { App() }
    }
}
