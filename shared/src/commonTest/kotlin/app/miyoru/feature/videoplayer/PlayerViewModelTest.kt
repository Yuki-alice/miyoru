package app.miyoru.feature.videoplayer

import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.domain.library.usecase.UpdateProgress
import app.miyoru.domain.source.model.ContentUnit
import app.miyoru.domain.source.model.ResolvedContent
import app.miyoru.domain.source.model.StreamResource
import app.miyoru.feature.FakeLibraryRepository
import app.miyoru.feature.FakeSourceRepository
import app.miyoru.feature.media
import app.miyoru.feature.videoplayer.presentation.PlayerViewModel
import app.miyoru.domain.source.model.MediaDetail
import app.miyoru.platform.player.PlayerFacade
import app.miyoru.platform.player.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakePlayer : PlayerFacade {
    val stateFlow = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = stateFlow.asStateFlow()
    var preparedUrl: String? = null
    var preparedHeaders: Map<String, String> = emptyMap()
    var plays = 0
    var pauses = 0
    var seeks = mutableListOf<Long>()
    override suspend fun prepare(url: String, headers: Map<String, String>, mimeType: String?) {
        preparedUrl = url
        preparedHeaders = headers
    }
    override fun play() { plays++; stateFlow.value = stateFlow.value.copy(isPlaying = true) }
    override fun pause() { pauses++ ; stateFlow.value = stateFlow.value.copy(isPlaying = false) }
    override fun seekTo(positionMillis: Long) { seeks.add(positionMillis) }
    override fun setSpeed(speed: Float) = Unit
    override fun release() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val key = MediaKey(SourceId("s"), MediaType.VIDEO, RemoteId("r"))

    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    private fun vm(sources: FakeSourceRepository, player: FakePlayer, lib: FakeLibraryRepository) =
        PlayerViewModel(key, RemoteId("e1"), sources, UpdateProgress(lib), player)

    @Test
    fun `loads stream and plays`() = runTest {
        val lib = FakeLibraryRepository()
        val player = FakePlayer()
        val sources = FakeSourceRepository(
            detail = MediaDetail(media("s", MediaType.VIDEO, "r", "V"), "Src"),
            units = listOf(ContentUnit("e1", "E1", 0)),
            resolveHandler = { _, _ -> ResolvedContent.Video(listOf(StreamResource("http://x/v.m3u8"))) },
        )
        val viewModel = vm(sources, player, lib)
        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals("http://x/v.m3u8", player.preparedUrl)
        assertEquals("V", state.title)
        assertEquals(1, player.plays)
        Unit
    }

    @Test
    fun `resolve failure surfaces error`() = runTest {
        val lib = FakeLibraryRepository()
        val sources = FakeSourceRepository(
            detail = MediaDetail(media("s", MediaType.VIDEO, "r", "V"), "Src"),
        )
        val viewModel = vm(sources, FakePlayer(), lib)
        val state = viewModel.uiState.first { !it.isLoading }
        assertTrue(state.errorMessage != null)
        Unit
    }
}
