package com.example.gamearchive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

internal data class BangumiSeason(
    val year: Int,
    val startMonth: Int
) {
    init {
        require(startMonth in listOf(1, 4, 7, 10))
    }

    val startDate: String
        get() = String.format(Locale.ROOT, "%04d-%02d-01", year, startMonth)

    val endExclusiveDate: String
        get() = if (startMonth == 10) {
            String.format(Locale.ROOT, "%04d-01-01", year + 1)
        } else {
            String.format(Locale.ROOT, "%04d-%02d-01", year, startMonth + 3)
        }

    val endInclusiveDate: String
        get() {
            val endMonth = startMonth + 2
            val endDay = when (endMonth) {
                3, 12 -> 31
                else -> 30
            }
            return String.format(Locale.ROOT, "%04d-%02d-%02d", year, endMonth, endDay)
        }

    fun previous(): BangumiSeason = if (startMonth == 1) {
        BangumiSeason(year - 1, 10)
    } else {
        BangumiSeason(year, startMonth - 3)
    }
}

internal fun currentBangumiSeason(calendar: Calendar = Calendar.getInstance()): BangumiSeason {
    val month = calendar.get(Calendar.MONTH) + 1
    return BangumiSeason(
        year = calendar.get(Calendar.YEAR),
        startMonth = ((month - 1) / 3) * 3 + 1
    )
}

internal fun bangumiSeasonsFrom(start: BangumiSeason, count: Int): List<BangumiSeason> {
    require(count >= 0)
    return buildList(count) {
        var season = start
        repeat(count) {
            add(season)
            season = season.previous()
        }
    }
}

internal fun BangumiSeason.toSearchRequest(): BangumiSubjectSearchRequest =
    BangumiSubjectSearchRequest(
        keyword = "",
        sort = "heat",
        filter = BangumiSubjectSearchFilter(
            type = listOf(2),
            meta_tags = listOf("TV", "日本"),
            air_date = listOf(">=$startDate", "<$endExclusiveDate")
        )
    )

internal data class BangumiSeasonSummaryUiState(
    val total: Int? = null,
    val previewSubjects: List<BangumiSubjectDetail> = emptyList(),
    val isLoading: Boolean = false,
    val failed: Boolean = false
)

internal data class BangumiSeasonDetailUiState(
    val season: BangumiSeason? = null,
    val subjects: List<BangumiSubjectDetail>? = null,
    val total: Int = 0,
    val nextOffset: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val failed: Boolean = false
) {
    val hasMore: Boolean
        get() = subjects != null && nextOffset < total
}

internal interface BangumiSeasonDataSource {
    suspend fun subjects(
        season: BangumiSeason,
        limit: Int,
        offset: Int
    ): BangumiPagedSubjects
}

private class NetworkBangumiSeasonDataSource : BangumiSeasonDataSource {
    override suspend fun subjects(
        season: BangumiSeason,
        limit: Int,
        offset: Int
    ): BangumiPagedSubjects = GameArchiveApp.bgmService.searchSubjects(
        payload = season.toSearchRequest(),
        limit = limit,
        offset = offset
    )
}

internal class BangumiSeasonViewModel internal constructor(
    private val dataSource: BangumiSeasonDataSource = NetworkBangumiSeasonDataSource(),
    private val pageSize: Int = 30
) : ViewModel() {

    private val _summaries = MutableStateFlow<Map<BangumiSeason, BangumiSeasonSummaryUiState>>(
        emptyMap()
    )
    val summaries: StateFlow<Map<BangumiSeason, BangumiSeasonSummaryUiState>> =
        _summaries.asStateFlow()

    private val _detail = MutableStateFlow(BangumiSeasonDetailUiState())
    val detail: StateFlow<BangumiSeasonDetailUiState> = _detail.asStateFlow()

    fun loadSummary(season: BangumiSeason, force: Boolean = false) {
        if (!force && _summaries.value.containsKey(season)) return
        viewModelScope.launch {
            _summaries.update { states ->
                states + (season to BangumiSeasonSummaryUiState(isLoading = true))
            }
            try {
                val summary = loadSummaryPages(season)
                _summaries.update { states ->
                    states + (season to summary)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _summaries.update { states ->
                    states + (season to BangumiSeasonSummaryUiState(failed = true))
                }
                android.util.Log.e("BangumiSeason", "Season summary load failed", error)
            }
        }
    }

    private suspend fun loadSummaryPages(season: BangumiSeason): BangumiSeasonSummaryUiState {
        val subjectsById = linkedMapOf<Int, BangumiSubjectDetail>()
        var offset = 0
        while (true) {
            val response = dataSource.subjects(
                season = season,
                limit = SUMMARY_PAGE_SIZE,
                offset = offset
            )
            val rawSubjects = response.data.orEmpty()
            rawSubjects.filterSeasonalAnime().forEach { subject ->
                subject.id?.let { subjectsById.putIfAbsent(it, subject) }
            }
            if (rawSubjects.isEmpty() || offset + rawSubjects.size >= response.total) break
            offset += rawSubjects.size
        }
        return BangumiSeasonSummaryUiState(
            total = subjectsById.size,
            previewSubjects = subjectsById.values.take(SUMMARY_PREVIEW_COUNT)
        )
    }

    fun openSeason(season: BangumiSeason) {
        if (_detail.value.season == season && _detail.value.subjects != null) return
        _detail.value = BangumiSeasonDetailUiState(season = season)
        loadFirstPage()
    }

    fun closeSeason() {
        _detail.value = BangumiSeasonDetailUiState()
    }

    fun retryDetail() {
        loadFirstPage()
    }

    fun loadNextPage() {
        val state = _detail.value
        if (
            state.season == null ||
            state.subjects == null ||
            state.isLoading ||
            state.isLoadingMore ||
            !state.hasMore
        ) {
            return
        }
        loadPage(season = state.season, offset = state.nextOffset, reset = false)
    }

    private fun loadFirstPage() {
        val season = _detail.value.season ?: return
        loadPage(season = season, offset = 0, reset = true)
    }

    private fun loadPage(season: BangumiSeason, offset: Int, reset: Boolean) {
        viewModelScope.launch {
            _detail.update { state ->
                if (reset) {
                    BangumiSeasonDetailUiState(
                        season = season,
                        isLoading = true
                    )
                } else {
                    state.copy(isLoadingMore = true, failed = false)
                }
            }
            try {
                val response = dataSource.subjects(season, pageSize, offset)
                val rawSubjects = response.data.orEmpty()
                val pageSubjects = rawSubjects.filterSeasonalAnime()
                _detail.update { state ->
                    if (state.season != season) return@update state
                    val existing = if (reset) emptyList() else state.subjects.orEmpty()
                    val nextOffset = if (rawSubjects.isEmpty()) {
                        response.total
                    } else {
                        offset + rawSubjects.size
                    }
                    state.copy(
                        subjects = (existing + pageSubjects).distinctBy { it.id },
                        total = response.total,
                        nextOffset = nextOffset,
                        isLoading = false,
                        isLoadingMore = false,
                        failed = false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _detail.update { state ->
                    if (state.season != season) state else state.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        failed = true
                    )
                }
                android.util.Log.e("BangumiSeason", "Season subject load failed", error)
            }
        }
    }

    private companion object {
        const val SUMMARY_PAGE_SIZE = 100
        const val SUMMARY_PREVIEW_COUNT = 3
    }
}

internal fun BangumiSubjectDetail.isSeasonalAnime(): Boolean {
    val normalizedMetaTags = meta_tags.orEmpty().map { it.trim() }
    val normalizedTags = tags.orEmpty().map { it.name.trim() }
    val excludedTags = setOf(
        "TVSP",
        "OVA",
        "OAD",
        "中国",
        "国产动画",
        "国产漫画",
        "国创",
        "动态漫画",
        "动态漫",
        "子供向",
        "短片"
    )
    return id != null &&
        type == 2 &&
        platform.equals("TV", ignoreCase = true) &&
        normalizedMetaTags.any { it.equals("TV", ignoreCase = true) } &&
        normalizedMetaTags.any { it == "日本" } &&
        (normalizedMetaTags + normalizedTags).none { tag ->
            excludedTags.any { excluded -> tag.equals(excluded, ignoreCase = true) }
        }
}

private fun List<BangumiSubjectDetail>.filterSeasonalAnime(): List<BangumiSubjectDetail> =
    filter(BangumiSubjectDetail::isSeasonalAnime)
