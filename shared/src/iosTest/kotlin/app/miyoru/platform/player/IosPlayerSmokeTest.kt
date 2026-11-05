package app.miyoru.platform.player

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IosPlayerSmokeTest {
    @Test
    fun createsAndPreparesWithoutCrashing() = runBlocking {
        val player = createPlayerFacade()
        assertFalse(player.state.value.isPlaying)
        // 不可达地址：不断言播放成功，只断言不崩溃且状态机可用
        player.prepare("https://127.0.0.1:9/nope.m3u8", emptyMap(), null)
        player.pause()
        player.release()
        Unit
    }
}
