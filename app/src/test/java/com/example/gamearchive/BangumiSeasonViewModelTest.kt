package com.example.gamearchive

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.GregorianCalendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BangumiSeasonViewModelTest {

    @Test
    fun currentSeasonUsesCalendarQuarter() {
        assertEquals(
            BangumiSeason(2026, 1),
            currentBangumiSeason(GregorianCalendar(2026, Calendar.MARCH, 31))
        )
        assertEquals(
            BangumiSeason(2026, 4),
            currentBangumiSeason(GregorianCalendar(2026, Calendar.APRIL, 1))
        )
        assertEquals(
            BangumiSeason(2026, 10),
            currentBangumiSeason(GregorianCalendar(2026, Calendar.DECEMBER, 31))
        )
    }

    @Test
    fun seasonSequenceCrossesYearBoundary() {
        assertEquals(
            listOf(
                BangumiSeason(2026, 4),
                BangumiSeason(2026, 1),
                BangumiSeason(2025, 10),
                BangumiSeason(2025, 7)
            ),
            bangumiSeasonsFrom(BangumiSeason(2026, 4), 4)
        )
    }

    @Test
    fun searchRequestUsesTvAndQuarterDateRange() {
        val request = BangumiSeason(2026, 10).toSearchRequest()

        assertEquals("", request.keyword)
        assertEquals("heat", request.sort)
        assertEquals(listOf(2), request.filter.type)
        assertEquals(listOf("TV", "日本"), request.filter.meta_tags)
        assertEquals(
            listOf(">=2026-10-01", "<2027-01-01"),
            request.filter.air_date
        )
    }

    @Test
    fun seasonalAnimeExcludesNonTvAndNonJapaneseEntries() {
        assertTrue(subject(1).isSeasonalAnime())
        assertFalse(subject(2, platform = "OVA").isSeasonalAnime())
        assertFalse(subject(3, metaTags = listOf("TV", "中国")).isSeasonalAnime())
        assertFalse(subject(4, extraTags = listOf("动态漫画")).isSeasonalAnime())
        assertFalse(subject(5, extraTags = listOf("TVSP")).isSeasonalAnime())
        assertFalse(subject(6, extraTags = listOf("子供向")).isSeasonalAnime())
        assertFalse(subject(7, extraTags = listOf("短片")).isSeasonalAnime())
    }

    @Test
    fun summaryCountsOnlyFilteredSubjectsAcrossAllPages() = runBlocking {
        val season = BangumiSeason(2026, 7)
        val requestedOffsets = mutableListOf<Int>()
        val source = object : BangumiSeasonDataSource {
            override suspend fun subjects(
                season: BangumiSeason,
                limit: Int,
                offset: Int
            ): BangumiPagedSubjects {
                requestedOffsets += offset
                val data = when (offset) {
                    0 -> listOf(subject(1), subject(2, extraTags = listOf("短片")))
                    else -> listOf(subject(3), subject(4, platform = "OVA"))
                }
                return BangumiPagedSubjects(
                    total = 4,
                    limit = limit,
                    offset = offset,
                    data = data
                )
            }
        }
        val viewModel = BangumiSeasonViewModel(source)

        viewModel.loadSummary(season)
        val summary = withTimeout(5_000) {
            viewModel.summaries.first { it[season]?.total != null }.getValue(season)
        }

        assertEquals(listOf(0, 2), requestedOffsets)
        assertEquals(2, summary.total)
        assertEquals(listOf(1, 3), summary.previewSubjects.map { it.id })
    }

    @Test
    fun detailPaginationStopsAtTotalAndDeduplicates() = runBlocking {
        val requestedOffsets = mutableListOf<Int>()
        val source = object : BangumiSeasonDataSource {
            override suspend fun subjects(
                season: BangumiSeason,
                limit: Int,
                offset: Int
            ): BangumiPagedSubjects {
                requestedOffsets += offset
                val data = when (offset) {
                    0 -> listOf(subject(1), subject(2))
                    else -> listOf(subject(2), subject(3))
                }
                return BangumiPagedSubjects(
                    total = 4,
                    limit = limit,
                    offset = offset,
                    data = data
                )
            }
        }
        val viewModel = BangumiSeasonViewModel(source, pageSize = 2)

        viewModel.openSeason(BangumiSeason(2026, 7))
        val firstPage = withTimeout(5_000) {
            viewModel.detail.first { !it.isLoading && it.subjects?.size == 2 }
        }
        assertTrue(firstPage.hasMore)

        viewModel.loadNextPage()
        val secondPage = withTimeout(5_000) {
            viewModel.detail.first { !it.isLoadingMore && it.nextOffset == 4 }
        }

        assertEquals(listOf(0, 2), requestedOffsets)
        assertEquals(listOf(1, 2, 3), secondPage.subjects.orEmpty().map { it.id })
        assertFalse(secondPage.hasMore)
    }

    private fun subject(
        id: Int,
        platform: String = "TV",
        metaTags: List<String> = listOf("TV", "日本"),
        extraTags: List<String> = emptyList()
    ) = BangumiSubjectDetail(
        id = id,
        name = "Subject $id",
        type = 2,
        platform = platform,
        meta_tags = metaTags,
        tags = extraTags.map { BangumiTag(it, 1) },
        date = "2026-07-01"
    )
}
