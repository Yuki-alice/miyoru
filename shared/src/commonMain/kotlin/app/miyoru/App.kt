package app.miyoru

import androidx.compose.runtime.Composable
import app.miyoru.core.ui.theme.MiyoruTheme
import app.miyoru.navigation.MainScaffold

@Composable
fun App() {
    MiyoruTheme {
        MainScaffold()
    }
}
