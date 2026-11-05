package app.miyoru.core.common

interface AppDispatchers {
    val main: kotlin.coroutines.CoroutineContext
    val io: kotlin.coroutines.CoroutineContext
    val default: kotlin.coroutines.CoroutineContext
}
