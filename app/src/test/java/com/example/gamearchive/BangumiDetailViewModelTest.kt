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
class BangumiDetailViewModelTest {
    @Test
    fun keepsPartialResultWhenOneEndpointFails() = runBlocking {
        val source = object : BangumiDetailDataSource {
            override suspend fun detail(subjectId: Int) = BangumiSubjectDetail(
                id = subjectId,
                name = "Subject"
            )

            override suspend fun persons(subjectId: Int): List<BangumiPerson> =
                error("persons unavailable")

            override suspend fun characters(subjectId: Int) = emptyList<BangumiRelatedCharacter>()
            override suspend fun relatedSubjects(subjectId: Int) = emptyList<BangumiRelatedSubject>()
            override suspend fun legacySubject(subjectId: Int): BangumiLegacySubject? = null
            override suspend fun episodes(subjectId: Int): BangumiPagedEpisodes? = null
        }
        val viewModel = BangumiDetailViewModel(source)

        viewModel.load(42)
        val state = withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }

        assertEquals(42, state.detail?.id)
        assertEquals(emptyList<BangumiPerson>(), state.persons)
        assertNull(state.collection.episodeCount)
        assertEquals(emptyList<BangumiUserEpisodeCollection>(), state.collection.episodes)
        assertFalse(state.isLoading)
    }

    @Test
    fun episodePaginationStopsAtTotalAndPreservesOrder() = runBlocking {
        val requestedOffsets = mutableListOf<Int>()
        val result = loadAllBangumiEpisodes(subjectId = 7, pageSize = 2) { _, limit, offset ->
            requestedOffsets += offset
            val data = when (offset) {
                0 -> listOf(BangumiEpisode(1, 0), BangumiEpisode(2, 0))
                2 -> listOf(BangumiEpisode(3, 0), BangumiEpisode(4, 0))
                else -> listOf(BangumiEpisode(5, 0))
            }
            BangumiPagedEpisodes(total = 5, limit = limit, offset = offset, data = data)
        }

        assertEquals(listOf(0, 2, 4), requestedOffsets)
        assertEquals(listOf(1, 2, 3, 4, 5), result.data.orEmpty().map { it.id })
    }
}
