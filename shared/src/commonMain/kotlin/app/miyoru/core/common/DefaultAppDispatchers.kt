package app.miyoru.core.common

import kotlinx.coroutines.Dispatchers

class DefaultAppDispatchers : AppDispatchers {
    override val main = Dispatchers.Main
    override val io = Dispatchers.Default
    override val default = Dispatchers.Default
}
