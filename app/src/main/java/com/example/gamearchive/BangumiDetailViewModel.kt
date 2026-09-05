package com.example.gamearchive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import retrofit2.HttpException
import java.util.Locale

data class BangumiCollectionState(
    val value: BangumiMyCollection? = null,
    val episodes: List<BangumiUserEpisodeCollection> = emptyList(),
    val episodeCount: Int? = null,
    val savedEpisodeProgress: Int = 0,
    val savedRegularEpisodeProgress: Int = 0,
    val isEpisodeProgressUnavailable: Boolean = false,
    val isLoaded: Boolean = false
)

data class BangumiCollectionDraft(
    // 使用 API 原始收藏类型：2 为看过，3 为在看。
    val type: Int = 0,
    val rate: Int = 0,
    val tags: String = "",
    val comment: String = "",
    val episodeProgress: Int = 0
)

data class BangumiDetailMessage(
    val id: Long,
    val resourceId: Int,
    val longDuration: Boolean = false
)

data class BangumiDetailUiState(
    val detail: BangumiSubjectDetail? = null,
    val persons: List<BangumiPerson> = emptyList(),
    val characters: List<BangumiRelatedCharacter> = emptyList(),
    val relatedSubjects: List<BangumiRelatedSubject> = emptyList(),
    val legacySubject: BangumiLegacySubject? = null,
    val isLoading: Boolean = true,
    val collection: BangumiCollectionState = BangumiCollectionState(),
    val draft: BangumiCollectionDraft = BangumiCollectionDraft(),
    val isSaving: Boolean = false,
    val tagSuggestions: List<String> = emptyList(),
    val isTagsLoading: Boolean = false,
    val saveRevision: Int = 0,
    val messages: List<BangumiDetailMessage> = emptyList()
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
    private val dataSource: BangumiDetailDataSource = NetworkBangumiDetailDataSource(),
    private var collectionSource: BangumiCollectionDataSource? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(BangumiDetailUiState())
    val uiState: StateFlow<BangumiDetailUiState> = _uiState.asStateFlow()
    private var loadedSubjectId: Int? = null
    private var subjectName = ""
    private var subjectNameCn = ""
    private var subjectImage = ""
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var tagsJob: Job? = null
    private var hasRequestedTags = false
    private var draftRevision = 0L
    private var nextMessageId = 0L

    fun load(
        subjectId: Int,
        context: Context? = null,
        subjectName: String = "",
        subjectNameCn: String = "",
        subjectImage: String = ""
    ) {
        if (loadedSubjectId == subjectId) return
        loadJob?.cancel()
        saveJob?.cancel()
        tagsJob?.cancel()
        loadedSubjectId = subjectId
        this.subjectName = subjectName
        this.subjectNameCn = subjectNameCn
        this.subjectImage = subjectImage
        if (collectionSource == null && context != null) {
            collectionSource = DefaultBangumiCollectionDataSource(context)
        }
        hasRequestedTags = false
        draftRevision = 0
        _uiState.value = BangumiDetailUiState()
        loadJob = viewModelScope.launch {
            try {
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
                    val episodePage = episodes.await()
                    _uiState.value = BangumiDetailUiState(
                        detail = detail.await(),
                        persons = persons.await(),
                        characters = characters.await(),
                        relatedSubjects = relatedSubjects.await(),
                        legacySubject = legacySubject.await(),
                        collection = BangumiCollectionState(
                            episodeCount = episodePage?.total?.takeIf { it > 0 },
                            episodes = episodePage?.data.orEmpty()
                                .map { BangumiUserEpisodeCollection(it, 0) }
                                .sortedWith(regularEpisodeOrder)
                        ),
                        isLoading = false
                    )
                }
                if (_uiState.value.detail == null) postMessage(R.string.bangumi_detail_load_failed)
                loadCollection(subjectId)
            } finally {
                if (loadedSubjectId == subjectId) {
                    _uiState.update {
                        it.copy(isLoading = false, collection = it.collection.copy(isLoaded = true))
                    }
                }
            }
        }
    }

    private suspend fun loadCollection(subjectId: Int) {
        val source = collectionSource?.takeIf { it.authorized } ?: return
        val initialRevision = draftRevision
        val result = runCatchingCancellable {
            source.authorizedRequest { service ->
                val username = resolveUsername(source, service)
                val collection = service.getMyCollection(username, subjectId)
                val episodes = try {
                    service.getEpisodeCollections(subjectId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: HttpException) {
                    if (error.code() == 401) throw error
                    null
                } catch (_: Exception) {
                    null
                }
                collection to episodes
            }
        }
        val loaded = result.getOrNull()
        if (loaded == null) {
            val error = result.exceptionOrNull()
            if ((error as? HttpException)?.code() != 404) {
                val message = if ((error as? HttpException)?.code() == 401) {
                    R.string.bangumi_authorization_expired
                } else {
                    R.string.bangumi_collection_load_failed
                }
                postMessage(message, longDuration = true)
            }
            return
        }
        val (collection, episodePage) = loaded
        val ordered = collection.copy(
            tags = BangumiTagOrder.restore(collection.tags, source.preferredTags(subjectId))
        )
        val episodes = episodePage?.data.orEmpty().sortedWith(regularEpisodeOrder)
        val savedProgress = if (episodePage != null) {
            episodes.count { it.type == 2 }
        } else {
            ordered.ep_status ?: 0
        }
        _uiState.update { state ->
            val mainEpisodes = if (episodePage != null) episodes else state.collection.episodes
            state.copy(
                collection = state.collection.copy(
                    value = ordered,
                    episodes = mainEpisodes,
                    savedEpisodeProgress = savedProgress,
                    savedRegularEpisodeProgress = savedProgress,
                    isEpisodeProgressUnavailable = episodePage == null
                ),
                draft = if (draftRevision == initialRevision) {
                    BangumiCollectionDraft(
                        type = ordered.type ?: 1,
                        rate = ordered.rate ?: 0,
                        tags = ordered.tags.orEmpty().joinToString(", "),
                        comment = ordered.comment.orEmpty(),
                        episodeProgress = if (ordered.type == 2 && mainEpisodes.isNotEmpty()) {
                            mainEpisodes.size
                        } else savedProgress
                    )
                } else state.draft
            )
        }
    }

    fun updateType(type: Int) = editDraft { draft ->
        val episodes = _uiState.value.collection.episodes
        draft.copy(
            type = type,
            episodeProgress = if (type == 2 && episodes.isNotEmpty()) episodes.size else draft.episodeProgress
        )
    }

    fun updateRate(rate: Int) = editDraft { it.copy(rate = rate) }
    fun updateTags(tags: String) = editDraft { it.copy(tags = tags) }
    fun updateComment(comment: String) = editDraft { it.copy(comment = comment) }
    fun updateEpisodeProgress(progress: Int) = editDraft { it.copy(episodeProgress = progress) }

    private fun editDraft(change: (BangumiCollectionDraft) -> BangumiCollectionDraft) {
        val draft = _uiState.value.draft
        val updated = change(draft)
        if (updated == draft) return
        draftRevision++
        _uiState.update { it.copy(draft = updated) }
    }

    fun loadTagSuggestions() {
        val source = collectionSource?.takeIf { it.authorized } ?: return
        val subjectId = loadedSubjectId ?: return
        if (hasRequestedTags || tagsJob?.isActive == true) return
        hasRequestedTags = true
        _uiState.update { it.copy(isTagsLoading = true) }
        tagsJob = viewModelScope.launch {
            try {
                val cached = source.cachedTagGroups()
                _uiState.update { it.copy(tagSuggestions = rankBangumiTags(cached)) }
                val remote = runCatchingCancellable {
                    source.authorizedRequest { service ->
                        val username = resolveUsername(source, service)
                        val groups = mutableListOf<List<String>>()
                        var offset = 0
                        while (true) {
                            val page = service.getUserCollections(
                                username, collectionType = null, limit = 50, offset = offset
                            )
                            val collections = page.data.orEmpty()
                            groups += collections.map { it.tags.orEmpty() }
                            if (collections.isEmpty() || offset + collections.size >= page.total) break
                            offset += collections.size
                        }
                        groups
                    }
                }.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(tagSuggestions = appendNewBangumiTags(it.tagSuggestions, rankBangumiTags(remote + cached)))
                }
            } finally {
                if (loadedSubjectId == subjectId) _uiState.update { it.copy(isTagsLoading = false) }
            }
        }
    }

    fun saveCollection() {
        val source = collectionSource?.takeIf { it.authorized } ?: return
        val subjectId = loadedSubjectId ?: return
        val submittedState = _uiState.value
        if (submittedState.isSaving || submittedState.draft.type == 0) return
        val submittedRevision = draftRevision
        val draft = submittedState.draft
        val tags = parseCollectionTags(draft.tags)
        _uiState.update { it.copy(isSaving = true) }
        saveJob = viewModelScope.launch {
            try {
                source.authorizedRequest { service ->
                    val username = resolveUsername(source, service)
                    saveRemoteCollection(source, service, username, subjectId, submittedState, tags)
                    source.saveTags(subjectId, tags)
                    val episodes = saveEpisodes(service, subjectId, submittedState)
                    val refreshed = service.getMyCollection(username, subjectId)
                    val progress = if (draft.type == 2) {
                        resolvedCompletedEpisodeCount(
                            loadedEpisodeCount = episodes.size,
                            episodePageTotal = submittedState.collection.episodeCount,
                            subjectEpisodeCount = submittedState.detail?.eps,
                            collectionEpisodeCount = refreshed.ep_status
                        )
                    } else draft.episodeProgress.coerceIn(0, episodes.size)
                    val ordered = refreshed.copy(tags = BangumiTagOrder.restore(refreshed.tags, tags))
                    applyConfirmedCollection(ordered, submittedRevision)
                    source.recordSave(
                        BangumiCollectionRecord(
                            subjectId = subjectId,
                            title = submittedState.detail?.name ?: subjectName,
                            secondaryTitle = submittedState.detail?.name_cn ?: subjectNameCn,
                            imageUrl = submittedState.detail?.images?.large
                                ?: submittedState.detail?.images?.common ?: subjectImage,
                            previousApiType = submittedState.collection.value?.type ?: 0,
                            currentApiType = draft.type,
                            previousEpisodes = submittedState.collection.savedRegularEpisodeProgress,
                            currentEpisodes = progress,
                            currentEpisodeIds = episodes.filter { it.type == 2 }.map { it.episode.id }
                        )
                    )
                    _uiState.update { state ->
                        state.copy(
                            saveRevision = state.saveRevision + 1,
                            collection = state.collection.copy(
                                episodes = episodes,
                                savedEpisodeProgress = progress,
                                savedRegularEpisodeProgress = progress
                            ),
                            draft = if (draftRevision == submittedRevision) {
                                state.draft.copy(episodeProgress = progress)
                            } else state.draft
                        )
                    }
                    val cached = collectionForCache(
                        subjectId, ordered, submittedState.detail, subjectName, subjectNameCn, draft, progress
                    )
                    source.updateCache(
                        collection = cached,
                        rating = normalizeBangumiScore(cached.subject?.rating),
                        episodeTotal = submittedState.collection.episodeCount
                            ?: episodes.size.takeIf { it > 0 } ?: submittedState.detail?.eps,
                        watchedCount = progress
                    )
                    source.notifyCollectionChanged()
                    postMessage(R.string.bangumi_collection_saved)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = if ((error as? HttpException)?.code() == 401) {
                    R.string.bangumi_authorization_expired
                } else {
                    R.string.bangumi_collection_save_failed
                }
                postMessage(message)
            } finally {
                if (loadedSubjectId == subjectId) _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun saveRemoteCollection(
        source: BangumiCollectionDataSource,
        service: BangumiCollectionService,
        username: String,
        subjectId: Int,
        state: BangumiDetailUiState,
        tags: List<String>
    ) {
        val draft = state.draft
        val comment = draft.comment.trim()
        val private = state.collection.value?.private ?: false
        val status = legacyBangumiCollectionStatus(draft.type)
        val legacySaved = status != null && runCatchingCancellable {
            val response = service.updateCollectionLegacy(
                subjectId = subjectId,
                appId = source.clientId,
                status = status,
                tags = tags.joinToString(" "),
                comment = comment,
                rating = draft.rate,
                privacy = if (private) 1 else 0
            )
            if (!response.isSuccessful || response.body()?.code != null || response.body()?.error != null) {
                return@runCatchingCancellable false
            }
            val verified = service.getMyCollection(username, subjectId)
            verified.type == draft.type && verified.rate == draft.rate &&
                verified.comment.orEmpty() == comment && verified.private == private &&
                BangumiTagOrder.hasSameOrder(verified.tags, tags)
        }.getOrDefault(false)
        if (!legacySaved) {
            val response = service.updateCollection(
                subjectId, BangumiCollectionUpdate(draft.type, draft.rate, comment, tags, private)
            )
            if (!response.isSuccessful) throw HttpException(response)
        }
    }

    private suspend fun saveEpisodes(
        service: BangumiCollectionService,
        subjectId: Int,
        state: BangumiDetailUiState
    ): List<BangumiUserEpisodeCollection> {
        val episodes = if (state.draft.type == 2 && state.collection.episodes.isEmpty()) {
            runCatchingCancellable {
                service.getEpisodeCollections(subjectId).data.orEmpty().sortedWith(regularEpisodeOrder)
            }.getOrDefault(emptyList())
        } else state.collection.episodes
        val change = collectionEpisodeChange(
            episodes, state.draft.type, state.collection.savedEpisodeProgress, state.draft.episodeProgress
        )
        if (change.episodeIds.isNotEmpty()) {
            val response = service.updateEpisodeCollections(
                subjectId, BangumiEpisodeCollectionUpdate(change.episodeIds, change.targetType)
            )
            if (!response.isSuccessful) throw HttpException(response)
        }
        val changedIds = change.episodeIds.toHashSet()
        return episodes.map { if (it.episode.id in changedIds) it.copy(type = change.targetType) else it }
    }

    private fun applyConfirmedCollection(collection: BangumiMyCollection, submittedRevision: Long) {
        _uiState.update { state ->
            state.copy(
                collection = state.collection.copy(value = collection),
                draft = if (draftRevision == submittedRevision) {
                    state.draft.copy(tags = collection.tags.orEmpty().joinToString(", "))
                } else state.draft,
                tagSuggestions = appendNewBangumiTags(state.tagSuggestions, collection.tags.orEmpty())
            )
        }
    }

    private suspend fun resolveUsername(
        source: BangumiCollectionDataSource,
        service: BangumiCollectionService
    ): String {
        if (source.username.isBlank()) source.username = service.getCurrentUser().username
        return source.username
    }

    private fun postMessage(resourceId: Int, longDuration: Boolean = false) {
        val message = BangumiDetailMessage(++nextMessageId, resourceId, longDuration)
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    fun consumeMessage(id: Long) {
        _uiState.update { state -> state.copy(messages = state.messages.filterNot { it.id == id }) }
    }
}

private val regularEpisodeOrder = compareBy<BangumiUserEpisodeCollection>(
    { it.episode.ep ?: Double.MAX_VALUE }, { it.episode.sort ?: Double.MAX_VALUE }
)

internal data class BangumiEpisodeChange(val episodeIds: List<Int>, val targetType: Int)

internal fun collectionEpisodeChange(
    episodes: List<BangumiUserEpisodeCollection>,
    apiType: Int,
    savedProgress: Int,
    draftProgress: Int
): BangumiEpisodeChange {
    val previous = savedProgress.coerceIn(0, episodes.size)
    val target = draftProgress.coerceIn(0, episodes.size)
    val changed = when {
        apiType == 2 -> episodes.filter { it.type != 2 }
        target > previous -> episodes.subList(previous, target)
        target < previous -> episodes.subList(target, previous)
        else -> emptyList()
    }
    return BangumiEpisodeChange(
        episodeIds = changed.map { it.episode.id },
        targetType = if (apiType == 2 || target > previous) 2 else 0
    )
}

internal fun parseCollectionTags(text: String): List<String> = text.split(Regex("[,，\\s]+"))
    .map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase() }

internal fun collectionForCache(
    subjectId: Int,
    collection: BangumiMyCollection,
    detail: BangumiSubjectDetail?,
    subjectName: String,
    subjectNameCn: String,
    draft: BangumiCollectionDraft,
    progress: Int
): BangumiCollection {
    val subject = collection.subject ?: detail?.let {
        BangumiSubject(
            id = subjectId,
            name = it.name ?: subjectName,
            name_cn = it.name_cn ?: subjectNameCn,
            type = it.type ?: 2,
            summary = it.summary,
            eps = it.eps,
            total_episodes = it.total_episodes,
            rating = it.rating,
            images = it.images,
            date = it.date,
            tags = it.tags
        )
    }
    return BangumiCollection(
        subject_id = collection.subject_id ?: subjectId,
        subject_type = collection.subject_type ?: 2,
        rate = collection.rate ?: draft.rate,
        type = bangumiCollectionTypeToUi(collection.type ?: draft.type),
        comment = collection.comment,
        tags = collection.tags,
        ep_status = progress,
        vol_status = collection.vol_status ?: 0,
        updated_at = collection.updated_at,
        private = collection.private ?: false,
        subject = subject
    )
}

private fun rankBangumiTags(tagGroups: Iterable<List<String>>): List<String> {
    val labels = linkedMapOf<String, String>()
    val counts = mutableMapOf<String, Int>()
    val firstSeen = mutableMapOf<String, Int>()
    var nextOrder = 0
    tagGroups.forEach { tags ->
        tags.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .forEach { tag ->
                val key = tag.lowercase(Locale.ROOT)
                labels.putIfAbsent(key, tag)
                counts[key] = counts.getOrDefault(key, 0) + 1
                firstSeen.putIfAbsent(key, nextOrder++)
            }
    }
    return labels.keys
        .sortedWith(
            compareByDescending<String> { counts.getOrDefault(it, 0) }
                .thenBy { firstSeen.getOrDefault(it, Int.MAX_VALUE) }
        )
        .mapNotNull(labels::get)
}

private fun appendNewBangumiTags(
    existing: List<String>,
    incoming: List<String>
): List<String> = buildList {
    addAll(existing)
    val existingKeys = existing
        .mapTo(mutableSetOf()) { it.trim().lowercase(Locale.ROOT) }
    incoming.forEach { tag ->
        if (existingKeys.add(tag.trim().lowercase(Locale.ROOT))) add(tag)
    }
}

internal fun resolvedCompletedEpisodeCount(
    loadedEpisodeCount: Int,
    episodePageTotal: Int?,
    subjectEpisodeCount: Int?,
    collectionEpisodeCount: Int?
): Int = sequenceOf(
    loadedEpisodeCount,
    episodePageTotal,
    subjectEpisodeCount,
    collectionEpisodeCount
).filterNotNull().firstOrNull { it > 0 } ?: 0

private fun legacyBangumiCollectionStatus(apiType: Int): String? = when (apiType) {
    1 -> "wish"
    2 -> "collect"
    3 -> "do"
    4 -> "on_hold"
    5 -> "dropped"
    else -> null
}
