package app.miyoru.feature.site

import app.miyoru.core.model.MediaType
import app.miyoru.core.model.Page
import app.miyoru.core.model.SourceId
import app.miyoru.feature.FakeSourceRepository
import app.miyoru.feature.media
import app.miyoru.feature.site.presentation.SiteViewModel
import app.miyoru.domain.source.usecase.SearchAcrossSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class SiteViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()); Unit }
    @AfterTest fun teardown() { Dispatchers.resetMain(); Unit }

    @Test
    fun `submit searches single source`() = runTest {
        val repo = FakeSourceRepository(
            searchHandler = { id, q -> Page(listOf(media(id.value, MediaType.NOVEL, "r", "Hit $q"))) },
        )
        val vm = SiteViewModel(SourceId("s"), SearchAcrossSources(repo, defaultPerSourceTimeoutMs = 5_000))
        vm.updateQuery("hello")
        vm.submit(MediaType.NOVEL)
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.first { !it.isSearching }
        assertEquals(listOf("Hit hello"), state.items.map { it.title })
        Unit
    }
}
