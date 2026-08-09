package com.example.gamearchive

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailViewModelTest {
    @Test
    fun keepsPartialResultWhenReviewsFail() = runBlocking {
        val details = StoreGameData(
            price_overview = null,
            short_description = "description",
            detailed_description = null,
            header_image = null,
            screenshots = null,
            movies = null,
            release_date = null,
            developers = null,
            publishers = null,
            name = "Game"
        )
        val source = object : DetailDataSource {
            override suspend fun details(appId: Int, language: String) = details
            override suspend fun reviews(appId: Int, language: String): ReviewResponse {
                error("reviews unavailable")
            }
        }
        val viewModel = DetailViewModel(source)

        viewModel.load(10, "schinese")
        val state = withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }

        assertEquals("Game", state.details?.name)
        assertNull(state.reviews)
        assertFalse(state.isLoading)
    }
}
