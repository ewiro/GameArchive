package com.example.gamearchive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class BangumiDetailUiState(
    val detail: BangumiSubjectDetail? = null,
    val persons: List<BangumiPerson> = emptyList(),
    val characters: List<BangumiRelatedCharacter> = emptyList(),
    val relatedSubjects: List<BangumiRelatedSubject> = emptyList(),
    val legacySubject: BangumiLegacySubject? = null,
    val episodes: BangumiPagedEpisodes? = null,
    val isLoading: Boolean = true
)

internal interface BangumiDetailDataSource {
    suspend fun detail(subjectId: Int): BangumiSubjectDetail?
    suspend fun persons(subjectId: Int): List<BangumiPerson>
    suspend fun characters(subjectId: Int): List<BangumiRelatedCharacter>
    suspend fun relatedSubjects(subjectId: Int): List<BangumiRelatedSubject>
    suspend fun legacySubject(subjectId: Int): BangumiLegacySubject?
    suspend fun episodes(subjectId: Int): BangumiPagedEpisodes?
}

private class NetworkBangumiDetailDataSource : BangumiDetailDataSource {
    override suspend fun detail(subjectId: Int) = GameArchiveApp.bgmService.getSubject(subjectId)
    override suspend fun persons(subjectId: Int) =
        GameArchiveApp.bgmService.getSubjectPersons(subjectId)
    override suspend fun characters(subjectId: Int) =
        GameArchiveApp.bgmService.getSubjectCharacters(subjectId)
    override suspend fun relatedSubjects(subjectId: Int) =
        GameArchiveApp.bgmService.getSubjectRelations(subjectId)
    override suspend fun legacySubject(subjectId: Int) =
        GameArchiveApp.bgmService.getLegacySubject(subjectId)

    override suspend fun episodes(subjectId: Int) = loadAllBangumiEpisodes(subjectId) {
            id, limit, offset ->
        GameArchiveApp.bgmService.getSubjectEpisodes(id, limit, offset)
    }
}

internal suspend fun loadAllBangumiEpisodes(
    subjectId: Int,
    pageSize: Int = 100,
    fetchPage: suspend (subjectId: Int, limit: Int, offset: Int) -> BangumiPagedEpisodes
): BangumiPagedEpisodes {
    val firstPage = fetchPage(subjectId, pageSize, 0)
    if (firstPage.total <= firstPage.data.orEmpty().size) return firstPage

    val episodes = firstPage.data.orEmpty().toMutableList()
    var offset = episodes.size
    while (offset < firstPage.total) {
        val page = fetchPage(subjectId, pageSize, offset)
        val newEpisodes = page.data.orEmpty()
        if (newEpisodes.isEmpty()) break
        episodes += newEpisodes
        offset += newEpisodes.size
    }
    return firstPage.copy(data = episodes)
}

class BangumiDetailViewModel internal constructor(
    private val dataSource: BangumiDetailDataSource = NetworkBangumiDetailDataSource()
) : ViewModel() {
    private val _uiState = MutableStateFlow(BangumiDetailUiState())
    val uiState: StateFlow<BangumiDetailUiState> = _uiState.asStateFlow()
    private var loadedSubjectId: Int? = null

    fun load(subjectId: Int) {
        if (loadedSubjectId == subjectId) return
        loadedSubjectId = subjectId
        viewModelScope.launch {
            _uiState.value = BangumiDetailUiState(isLoading = true)
            supervisorScope {
                val detail = async {
                    runCatchingCancellable { dataSource.detail(subjectId) }.getOrNull()
                }
                val persons = async {
                    runCatchingCancellable { dataSource.persons(subjectId) }.getOrDefault(emptyList())
                }
                val characters = async {
                    runCatchingCancellable { dataSource.characters(subjectId) }.getOrDefault(emptyList())
                }
                val relatedSubjects = async {
                    runCatchingCancellable { dataSource.relatedSubjects(subjectId) }
                        .getOrDefault(emptyList())
                }
                val legacySubject = async {
                    runCatchingCancellable { dataSource.legacySubject(subjectId) }.getOrNull()
                }
                val episodes = async {
                    runCatchingCancellable { dataSource.episodes(subjectId) }.getOrNull()
                }
                _uiState.value = BangumiDetailUiState(
                    detail = detail.await(),
                    persons = persons.await(),
                    characters = characters.await(),
                    relatedSubjects = relatedSubjects.await(),
                    legacySubject = legacySubject.await(),
                    episodes = episodes.await(),
                    isLoading = false
                )
            }
        }
    }
}
