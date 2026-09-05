package com.example.gamearchive

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import androidx.lifecycle.ViewModelStore
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BangumiCollectionEditorTest {
    @Test
    fun loadingRestoresTagOrderAndWatchedRegularProgress() = runBlocking {
        val source = FakeCollectionSource()
        val vm = load(source)
        val state = vm.uiState.value
        assertEquals("Second, First", state.draft.tags)
        assertEquals(1, state.collection.savedEpisodeProgress)
        assertEquals(1, state.draft.episodeProgress)
        assertEquals(listOf("collection", "episodes", "tagOrder"), source.calls)
        vm.load(42)
        assertEquals(3, source.calls.size)
    }

    @Test
    fun absentAuthorizationAndUncollectedSubjectKeepEmptyDraft() = runBlocking {
        val unauthorized = FakeCollectionSource().apply { authorized = false }
        assertEquals(0, load(unauthorized).uiState.value.draft.type)
        assertTrue(unauthorized.calls.isEmpty())
        val missing = FakeCollectionSource().apply { failAt = "collection"; failureCode = 404 }
        val state = load(missing).uiState.value
        assertEquals(0, state.draft.type)
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun episodeFailureKeepsCollectionProgressAndReportsUnavailable() = runBlocking {
        val source = FakeCollectionSource().apply { failAt = "episodes" }
        val state = load(source).uiState.value
        assertTrue(state.collection.isEpisodeProgressUnavailable)
        assertEquals(1, state.draft.episodeProgress)
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun authorizationFailureMessageIsConsumedOnce() = runBlocking {
        val vm = load(FakeCollectionSource().apply {
            failAt = "collection"
            failureCode = 401
        })
        val message = vm.uiState.value.messages.single()
        assertEquals(R.string.bangumi_authorization_expired, message.resourceId)
        vm.consumeMessage(message.id)
        vm.consumeMessage(message.id)
        assertTrue(vm.uiState.value.messages.isEmpty())
    }

    @Test
    fun verifiedLegacySavePreservesOrderAndSkipsFallback() = runBlocking {
        val source = FakeCollectionSource()
        val vm = load(source)
        source.calls.clear()
        vm.updateType(2)
        vm.updateRate(8)
        vm.updateTags("Second, first, FIRST")
        vm.updateComment("  comment  ")
        save(vm)
        assertEquals(listOf("legacy", "collection", "saveTags", "updateEpisodes",
            "collection", "record", "cache", "changed"), source.calls)
        assertEquals(BangumiCollectionUpdate(2, 8, "comment", listOf("Second", "first"), true), source.lastUpdate)
        assertEquals(listOf(2, 3), source.episodeUpdate!!.episode_id)
        assertEquals(3, source.cached!!.type)
    }

    @Test
    fun failedLegacyVerificationUsesFallbackAndUpdatesOnlyLocalCache() = runBlocking {
        val source = FakeCollectionSource().apply { legacyMatches = false }
        val vm = load(source)
        source.calls.clear()
        vm.updateType(2)
        save(vm)
        assertEquals(listOf("legacy", "collection", "fallback", "saveTags", "updateEpisodes",
            "collection", "record", "cache", "changed"), source.calls)
        assertEquals(3, source.cached!!.type)
        assertEquals(3, source.cached!!.ep_status)
    }

    @Test
    fun unsuccessfulLegacyResponseUsesFallback() = runBlocking {
        val source = FakeCollectionSource().apply { legacySucceeds = false }
        val vm = load(source)
        source.calls.clear()
        save(vm)
        assertEquals(listOf("legacy", "fallback", "saveTags", "collection", "record", "cache", "changed"), source.calls)
    }

    @Test
    fun episodeChangesHandleIncreaseDecreaseAndCompletion() {
        val episodes = FakeCollectionSource().episodes
        assertEquals(BangumiEpisodeChange(listOf(2, 3), 2), collectionEpisodeChange(episodes, 3, 1, 3))
        assertEquals(BangumiEpisodeChange(listOf(1), 0), collectionEpisodeChange(episodes, 3, 1, 0))
        assertEquals(BangumiEpisodeChange(listOf(2, 3), 2), collectionEpisodeChange(episodes, 2, 1, 0))
        assertTrue(collectionEpisodeChange(emptyList(), 2, 0, 0).episodeIds.isEmpty())
    }

    @Test
    fun savingUsesSnapshotAndPreservesNewEdits() = runBlocking {
        val source = FakeCollectionSource()
        val vm = load(source)
        source.saveGate = CompletableDeferred()
        vm.updateRate(8)
        vm.updateTags("submitted")
        vm.saveCollection()
        source.saveStarted.await()
        vm.saveCollection()
        vm.updateRate(9)
        vm.updateTags("new draft")
        vm.updateEpisodeProgress(2)
        source.saveGate!!.complete(Unit)
        awaitSaved(vm)
        assertEquals(8, source.lastUpdate!!.rate)
        assertEquals(listOf("submitted"), source.lastUpdate!!.tags)
        assertEquals(9, vm.uiState.value.draft.rate)
        assertEquals("new draft", vm.uiState.value.draft.tags)
        assertEquals(2, vm.uiState.value.draft.episodeProgress)
        assertEquals(1, source.calls.count { it == "legacy" })
    }

    @Test
    fun unchangedTagTextDoesNotPreventSaveReadback() = runBlocking {
        val source = FakeCollectionSource()
        val vm = load(source)
        vm.updateTags("Second,First")
        source.saveGate = CompletableDeferred()
        vm.saveCollection()
        source.saveStarted.await()
        // TextField 光标变化也会回调，但文本不变时不属于新草稿。
        vm.updateTags("Second,First")
        source.saveGate!!.complete(Unit)
        awaitSaved(vm)
        assertEquals("Second, First", vm.uiState.value.draft.tags)
    }

    @Test
    fun laterFailureKeepsEarlierSideEffectsAndNeverReportsSuccess() = runBlocking {
        for (stage in listOf("fallback", "updateEpisodes", "finalCollection")) {
            val source = FakeCollectionSource().apply { legacySucceeds = false }
            val vm = load(source)
            source.calls.clear()
            source.failAt = stage
            vm.updateType(2)
            vm.saveCollection()
            withTimeout(5_000) { vm.uiState.first { !it.isSaving } }
            assertEquals(R.string.bangumi_collection_save_failed, vm.uiState.value.messages.last().resourceId)
            assertFalse(source.calls.contains("record"))
            assertFalse(source.calls.contains("cache"))
            assertEquals(stage != "fallback", source.calls.contains("saveTags"))
        }
    }

    @Test
    fun cancellationResetsSavingWithoutFailureMessage() = runBlocking {
        val source = FakeCollectionSource()
        val vm = load(source)
        source.cancelSave = true
        vm.saveCollection()
        withTimeout(5_000) { vm.uiState.first { !it.isSaving } }
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertFalse(source.calls.contains("cache"))
    }

    @Test
    fun localWriteFailurePreservesConfirmedCollectionWithoutSuccessMessage() = runBlocking {
        for (stage in listOf("record", "cache")) {
            val source = FakeCollectionSource()
            val vm = load(source)
            source.failAt = stage
            vm.updateType(2)
            vm.saveCollection()
            withTimeout(5_000) { vm.uiState.first { !it.isSaving } }
            val state = vm.uiState.value
            assertEquals(2, state.collection.value!!.type)
            assertEquals(R.string.bangumi_collection_save_failed, state.messages.single().resourceId)
            assertEquals(stage == "cache", state.saveRevision == 1)
            assertFalse(source.calls.contains("changed"))
        }
    }

    @Test
    fun tagSuggestionsAppendWithoutReorderingOrReplacingDraft() = runBlocking {
        val source = FakeCollectionSource()
        val vm = load(source)
        vm.updateTags("editing")
        source.tagsGate = CompletableDeferred()
        vm.loadTagSuggestions()
        withTimeout(5_000) { vm.uiState.first { it.tagSuggestions.isNotEmpty() } }
        assertEquals(listOf("Local", "Shared"), vm.uiState.value.tagSuggestions)
        source.tagsGate!!.complete(Unit)
        withTimeout(5_000) { vm.uiState.first { !it.isTagsLoading } }
        assertEquals(listOf("Local", "Shared", "Remote"), vm.uiState.value.tagSuggestions)
        assertEquals("editing", vm.uiState.value.draft.tags)
        vm.loadTagSuggestions()
        assertEquals(1, source.calls.count { it == "tags" })
    }

    @Test
    fun repeatedCompletedSaveUsesUpdatedBaselineAndDoesNotRewriteEpisodes() = runBlocking {
        val source = FakeCollectionSource()
        val vm = load(source)
        vm.updateType(2)
        save(vm)
        save(vm)
        assertEquals(listOf(1, 3), source.records.map { it.previousEpisodes })
        assertEquals(listOf(3, 3), source.records.map { it.currentEpisodes })
        assertEquals(listOf(3, 2), source.records.map { it.previousApiType })
        assertEquals(1, source.calls.count { it == "updateEpisodes" })
    }

    @Test
    fun collectionStatusAlwaysUsesApiTypeAndCacheUsesUiType() = runBlocking {
        for (type in 1..5) {
            val source = FakeCollectionSource()
            val vm = load(source)
            vm.updateType(type)
            save(vm)
            assertEquals(type, source.lastUpdate!!.type)
            assertEquals(bangumiCollectionTypeToUi(type), source.cached!!.type)
        }
    }

    @Test
    fun completionWithoutReadableEpisodesKeepsExistingCountFallback() = runBlocking {
        val source = FakeCollectionSource().apply { episodes = emptyList() }
        val vm = load(source)
        source.failAt = "episodes"
        vm.updateType(2)
        save(vm)
        assertEquals(3, source.cached!!.ep_status)
        assertNull(source.episodeUpdate)
        assertEquals(emptyList<Int>(), source.records.single().currentEpisodeIds)
    }

    @Test
    fun failedTagSupplementKeepsLocalSuggestionsAndResetsLoading() = runBlocking {
        val source = FakeCollectionSource().apply { failAt = "tags" }
        val vm = load(source)
        vm.loadTagSuggestions()
        withTimeout(5_000) { vm.uiState.first { !it.isTagsLoading } }
        assertEquals(listOf("Local", "Shared"), vm.uiState.value.tagSuggestions)
        assertTrue(vm.uiState.value.messages.isEmpty())
    }

    @Test
    fun clearingViewModelCancelsSaveAndTagJobs() = runBlocking {
        val source = FakeCollectionSource()
        val vm = load(source)
        val store = ViewModelStore().apply { put("detail", vm) }
        source.saveGate = CompletableDeferred()
        source.tagsGate = CompletableDeferred()
        vm.saveCollection()
        vm.loadTagSuggestions()
        source.saveStarted.await()
        withTimeout(5_000) { vm.uiState.first { it.tagSuggestions.isNotEmpty() } }
        store.clear()
        withTimeout(5_000) { vm.uiState.first { !it.isSaving && !it.isTagsLoading } }
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertFalse(source.calls.contains("cache"))
    }

    @Test
    fun clearingViewModelCancelsCollectionLoadWithoutReportingFailure() = runBlocking {
        val source = FakeCollectionSource().apply { collectionGate = CompletableDeferred() }
        val vm = BangumiDetailViewModel(FakeDetailSource(source), source)
        val store = ViewModelStore().apply { put("detail", vm) }
        vm.load(42)
        source.collectionStarted.await()
        store.clear()
        withTimeout(5_000) { vm.uiState.first { it.collection.isLoaded && !it.isLoading } }
        assertTrue(vm.uiState.value.messages.isEmpty())
    }

    @Test
    fun tagParsingAndCacheFallbackPreserveExistingFields() {
        assertEquals(listOf("Second", "first", "第三"), parseCollectionTags(" Second， first FIRST\n第三 "))
        val cached = collectionForCache(42, BangumiMyCollection(),
            BangumiSubjectDetail(eps = 12), "Name", "中文名", BangumiCollectionDraft(type = 2, rate = 8), 12)
        assertEquals(42, cached.subject_id)
        assertEquals(3, cached.type)
        assertEquals(8, cached.rate)
        assertEquals("Name", cached.subject!!.name)
        assertEquals("中文名", cached.subject.name_cn)
        assertEquals(12, cached.ep_status)
    }

    private suspend fun load(source: FakeCollectionSource): BangumiDetailViewModel {
        val vm = BangumiDetailViewModel(FakeDetailSource(source), source)
        vm.load(42, subjectName = "Fallback", subjectImage = "cover")
        withTimeout(5_000) { vm.uiState.first { it.collection.isLoaded } }
        return vm
    }

    private suspend fun save(vm: BangumiDetailViewModel) {
        vm.saveCollection()
        awaitSaved(vm)
    }

    private suspend fun awaitSaved(vm: BangumiDetailViewModel) {
        withTimeout(5_000) { vm.uiState.first { !it.isSaving && it.saveRevision > 0 } }
    }
}

private class FakeDetailSource(private val source: FakeCollectionSource) : BangumiDetailDataSource {
    override suspend fun detail(subjectId: Int) = BangumiSubjectDetail(id = subjectId, eps = 3)
    override suspend fun persons(subjectId: Int) = emptyList<BangumiPerson>()
    override suspend fun characters(subjectId: Int) = emptyList<BangumiRelatedCharacter>()
    override suspend fun relatedSubjects(subjectId: Int) = emptyList<BangumiRelatedSubject>()
    override suspend fun legacySubject(subjectId: Int): BangumiLegacySubject? = null
    override suspend fun episodes(subjectId: Int) = BangumiPagedEpisodes(3, 100, 0, source.episodes.map { it.episode })
}

private class FakeCollectionSource : BangumiCollectionDataSource, BangumiCollectionService {
    override var authorized = true
    override var username = "user"
    override val clientId = "client"
    val calls = mutableListOf<String>()
    var collection = BangumiMyCollection(type = 3, rate = 7, tags = listOf("First", "Second"),
        comment = "", ep_status = 1, private = true)
    var episodes = (1..3).map { BangumiUserEpisodeCollection(BangumiEpisode(it, 0, ep = it.toDouble()), if (it == 1) 2 else 0) }
    val records = mutableListOf<BangumiCollectionRecord>()
    var lastUpdate: BangumiCollectionUpdate? = null
    var episodeUpdate: BangumiEpisodeCollectionUpdate? = null
    var cached: BangumiCollection? = null
    var legacySucceeds = true
    var legacyMatches = true
    var failAt = ""
    var failureCode = 500
    var cancelSave = false
    var saveGate: CompletableDeferred<Unit>? = null
    val saveStarted = CompletableDeferred<Unit>()
    var tagsGate: CompletableDeferred<Unit>? = null
    var collectionGate: CompletableDeferred<Unit>? = null
    val collectionStarted = CompletableDeferred<Unit>()

    private fun call(name: String) {
        calls += name
        if (name == failAt || (name == "collection" && failAt == "finalCollection" && calls.contains("saveTags"))) {
            throw HttpException(Response.error<Unit>(failureCode, "failure".toResponseBody()))
        }
    }

    override suspend fun <T> authorizedRequest(request: suspend (BangumiCollectionService) -> T): T = request(this)
    override suspend fun preferredTags(subjectId: Int): List<String> { call("tagOrder"); return listOf("Second", "First") }
    override suspend fun saveTags(subjectId: Int, tags: List<String>) { call("saveTags") }
    override suspend fun cachedTagGroups() = listOf(listOf("Local", "Shared"))
    override suspend fun recordSave(record: BangumiCollectionRecord) { call("record"); records += record }
    override suspend fun updateCache(collection: BangumiCollection, rating: Double?, episodeTotal: Int?, watchedCount: Int) { call("cache"); cached = collection }
    override fun notifyCollectionChanged() { call("changed") }
    override suspend fun getCurrentUser(): BangumiUser = error("username is configured")
    override suspend fun getMyCollection(username: String, subjectId: Int): BangumiMyCollection {
        call("collection")
        collectionStarted.complete(Unit)
        collectionGate?.await()
        return collection
    }
    override suspend fun getEpisodeCollections(subjectId: Int, limit: Int, episodeType: Int): BangumiPagedEpisodeCollection {
        call("episodes")
        return BangumiPagedEpisodeCollection(3, limit, 0, episodes)
    }
    override suspend fun getUserCollections(username: String, subjectType: Int, collectionType: Int?, limit: Int, offset: Int): BangumiPagedCollection {
        call("tags")
        tagsGate?.await()
        return BangumiPagedCollection(total = 1, limit = limit, offset = offset,
            data = listOf(BangumiCollection(subject_id = 42, subject_type = 2, rate = 0, type = 3,
                tags = listOf("Remote", "Shared"), comment = null, ep_status = 0, vol_status = 0,
                updated_at = null, private = false, subject = null)))
    }
    override suspend fun updateCollectionLegacy(subjectId: Int, appId: String, status: String, tags: String, comment: String, rating: Int, privacy: Int): Response<BangumiLegacyCollectionResult> {
        call("legacy")
        saveStarted.complete(Unit)
        saveGate?.await()
        if (cancelSave) throw CancellationException("cancelled")
        val type = mapOf("wish" to 1, "collect" to 2, "do" to 3, "on_hold" to 4, "dropped" to 5).getValue(status)
        lastUpdate = BangumiCollectionUpdate(type, rating, comment, tags.split(" "), privacy == 1)
        if (legacySucceeds && legacyMatches) applyUpdate(lastUpdate!!)
        return if (legacySucceeds) Response.success(BangumiLegacyCollectionResult()) else Response.error(500, "failure".toResponseBody())
    }
    override suspend fun updateCollection(subjectId: Int, payload: BangumiCollectionUpdate): Response<Unit> {
        call("fallback")
        lastUpdate = payload
        applyUpdate(payload)
        return Response.success(Unit)
    }
    override suspend fun updateEpisodeCollections(subjectId: Int, payload: BangumiEpisodeCollectionUpdate): Response<Unit> {
        call("updateEpisodes"); episodeUpdate = payload
        return Response.success(Unit)
    }
    private fun applyUpdate(payload: BangumiCollectionUpdate) {
        collection = collection.copy(type = payload.type, rate = payload.rate, comment = payload.comment, tags = payload.tags, private = payload.private)
    }
}
