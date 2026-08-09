package com.example.gamearchive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BangumiUiState(
    val user: BangumiUser? = null,
    val collections: Map<Int, List<BangumiCollection>>? = null,
    val ratings: Map<Int, Double> = emptyMap(),
    val episodeTotals: Map<Int, Int> = emptyMap(),
    val watchedEpisodeCounts: Map<Int, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: Pair<Int, String?>? = null
)

internal interface BangumiDataSource {
    suspend fun user(username: String): BangumiUser
    suspend fun collections(username: String, type: Int, offset: Int): BangumiPagedCollection
    suspend fun subject(subjectId: Int): BangumiSubjectDetail
    suspend fun episodeCollections(
        context: Context,
        subjectId: Int
    ): BangumiPagedEpisodeCollection
    suspend fun publicEpisodeTotal(subjectId: Int): Int
}

private class NetworkBangumiDataSource : BangumiDataSource {
    override suspend fun user(username: String) = GameArchiveApp.bgmService.getUserInfo(username)

    override suspend fun collections(username: String, type: Int, offset: Int) =
        GameArchiveApp.bgmService.getUserCollections(
            username,
            subjectType = 2,
            collectionType = type,
            limit = 50,
            offset = offset
        )

    override suspend fun subject(subjectId: Int) =
        GameArchiveApp.bgmService.getSubject(subjectId)

    override suspend fun episodeCollections(
        context: Context,
        subjectId: Int
    ) = BangumiAuthSession.execute(context) { it.getEpisodeCollections(subjectId) }

    override suspend fun publicEpisodeTotal(subjectId: Int) =
        GameArchiveApp.bgmService.getSubjectEpisodes(subjectId).total
}

/**
 * Bangumi（番组计划）动漫收藏页的"数据管家"。
 * 负责按收藏类型（想看/看过/在看等）联网拉取数据。
 */
class BangumiViewModel internal constructor(
    private val dataSource: BangumiDataSource = NetworkBangumiDataSource()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BangumiUiState())
    val uiState: StateFlow<BangumiUiState> = _uiState.asStateFlow()

    // 用户信息

    // 分组后的收藏: type(1-5) -> List<BangumiCollection>

    // subject_id → rating Map（从 /v0/subjects/{id} 补拉）

    // subject_id → 正篇章节总数（eps 缺失时由 type=0 章节接口补拉）

    // subject_id → 仅正篇中已看章节数




    private var hasLoaded = false
    private var isInitialLoadInProgress = false
    private var isRefreshInProgress = false

    /** 收藏类型 → 中文名 */
    companion object {
        @Volatile
        var collectionChanged = false

        val typeNames = mapOf(
            1 to R.string.bangumi_wish,
            2 to R.string.bangumi_doing,
            3 to R.string.bangumi_done,
            4 to R.string.bangumi_on_hold,
            5 to R.string.bangumi_dropped
        )
    }

    fun loadIfNeeded(username: String, accessToken: String, context: Context) {
        if (hasLoaded || isInitialLoadInProgress || isRefreshInProgress) return
        isInitialLoadInProgress = true
        _uiState.update {
            it.copy(isLoading = it.collections == null, error = null)
        }
        viewModelScope.launch(Dispatchers.Default) {
            val cached = try {
                val loadedCache = runCatchingCancellable {
                    withContext(Dispatchers.IO) {
                        BangumiPageCache.load(context, username) to
                            BangumiTagOrder.snapshot(context, username)
                    }
                }.getOrNull()
                loadedCache?.first?.also {
                    val tagOrders = loadedCache.second
                    _uiState.update { state ->
                        state.copy(
                            user = it.user ?: state.user,
                            collections = it.collections.restoreTagOrders(tagOrders),
                            ratings = it.ratings.mapNotNull { (id, value) ->
                                normalizeBangumiScore(value)?.let { score -> id to score }
                            }.toMap(),
                            episodeTotals = it.episodeTotals,
                            watchedEpisodeCounts = it.watchedEpisodeCounts,
                            isLoading = false
                        )
                    }
                    hasLoaded = true
                }
            } finally {
                isInitialLoadInProgress = false
            }
            if (cached == null) {
                loadFromNetwork(username, accessToken, context, showRefreshIndicator = false)
            } else if (accessToken.isNotBlank()) {
                runCatchingCancellable {
                    syncCachedWatchingActivity(context, cached.collections)
                }.onFailure { error ->
                    android.util.Log.w(
                        "BangumiViewModel",
                        "Silent activity sync failed",
                        error
                    )
                }
            }
        }
    }

    fun refresh(username: String, accessToken: String, context: Context) {
        loadFromNetwork(username, accessToken, context, showRefreshIndicator = true)
    }

    private fun loadFromNetwork(
        username: String,
        accessToken: String,
        context: Context,
        showRefreshIndicator: Boolean
    ) {
        if (isRefreshInProgress) return
        if (username.isBlank()) {
            _uiState.update { it.copy(error = Pair(R.string.bangumi_no_username, null)) }
            return
        }
        isRefreshInProgress = true
        _uiState.update {
            it.copy(isRefreshing = showRefreshIndicator, error = null)
        }
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isLoading = it.collections == null) }
            try {
                val tagOrders = withContext(Dispatchers.IO) {
                    BangumiTagOrder.snapshot(context, username)
                }
                // 并发：用户信息 + 所有分类收藏同时拉取
                val userDeferred = async {
                    runCatchingCancellable {
                        dataSource.user(username)
                    }.getOrNull()
                }
                // 5 个分类并发拉取
                val typeDeferreds = (1..5).map { type ->
                    async {
                        val bucket = bangumiCollectionTypeToUi(type)
                        runCatchingCancellable {
                            val list = mutableListOf<BangumiCollection>()
                            var offset = 0
                            while (true) {
                                val page = dataSource.collections(username, type, offset)
                                page.data?.forEach { item ->
                                    val t = bangumiCollectionTypeToUi(item.type)
                                    list.add(
                                        item.copy(
                                            type = t,
                                            tags = BangumiTagOrder.restore(
                                                item.tags,
                                                tagOrders[item.subject_id]
                                            )
                                        )
                                    )
                                }
                                if (page.data == null || page.total <= offset + 50) break
                                offset += 50
                            }
                            CollectionBucketResult(bucket, list, succeeded = true)
                        }.getOrElse {
                            CollectionBucketResult(bucket, emptyList(), succeeded = false)
                        }
                    }
                }
                // 默认展示“在看”：优先等待这一类，先结束首屏骨架，再补齐其他分类。
                val watchingResult = typeDeferreds[2].await()
                if (watchingResult.succeeded) {
                    val earlyData = _uiState.value.collections.orEmpty()
                        .mapValuesTo(mutableMapOf()) { (_, list) -> list.toMutableList() }
                    if (watchingResult.items.isEmpty()) {
                        earlyData.remove(watchingResult.bucket)
                    } else {
                        earlyData[watchingResult.bucket] =
                            watchingResult.items.toMutableList()
                    }
                    _uiState.update { it.copy(collections = earlyData, isLoading = false) }
                }
                val bucketResults = typeDeferreds.awaitAll()
                if (bucketResults.none { it.succeeded }) {
                    _uiState.update {
                        it.copy(error = Pair(R.string.bangumi_collection_load_failed, null))
                    }
                    return@launch
                }
                val allData = _uiState.value.collections.orEmpty()
                    .mapValuesTo(mutableMapOf()) { (_, list) -> list.toMutableList() }
                bucketResults.filter { it.succeeded }.forEach { result ->
                    if (result.items.isEmpty()) {
                        allData.remove(result.bucket)
                    } else {
                        allData[result.bucket] = result.items.toMutableList()
                    }
                }
                val loadedUser = userDeferred.await()
                _uiState.update {
                    it.copy(
                        collections = allData,
                        user = loadedUser ?: it.user,
                        isLoading = false
                    )
                }

                // 批量拉取 subject 详情评分（并发 8 个一组）
                val existingRatings = _uiState.value.ratings.toMutableMap()
                val episodeTotals = _uiState.value.episodeTotals.toMutableMap()
                val watchedEpisodeCounts =
                    _uiState.value.watchedEpisodeCounts.toMutableMap()
                val watchedEpisodeCollections =
                    mutableMapOf<Int, List<BangumiUserEpisodeCollection>>()
                val allSubjects = allData.values.flatten()
                // 先收集已有评分的（collections API 可能部分返回了）
                for (item in allSubjects) {
                    val score = normalizeBangumiScore(item.subject?.rating)
                    if (score != null) existingRatings[item.subject_id] = score
                    val total = item.subject?.eps?.takeIf { it > 0 }
                    if (total != null) episodeTotals[item.subject_id] = total
                }
                _uiState.update {
                    it.copy(
                        ratings = existingRatings.toMap(),
                        episodeTotals = episodeTotals.toMap(),
                        watchedEpisodeCounts = watchedEpisodeCounts.toMap()
                    )
                }
                savePageCache(
                    context,
                    username,
                    allData,
                    existingRatings,
                    episodeTotals,
                    watchedEpisodeCounts
                )

                // 补拉完整详情评分
                val subjectIds = allSubjects.map { it.subject_id }.distinct()
                val missingRatingIds = allSubjects
                    .filter { it.subject?.rating == null }
                    .map { it.subject_id }
                    .distinct()
                if (missingRatingIds.isNotEmpty()) {
                    kotlinx.coroutines.coroutineScope {
                        missingRatingIds.chunked(8).forEach { batch ->
                            batch.map { id ->
                                async {
                                    runCatchingCancellable {
                                        id to dataSource.subject(id)
                                    }.getOrNull()
                                }
                            }.mapNotNull { it.await() }.forEach { (id, detail) ->
                                normalizeBangumiScore(detail.rating)?.let { score ->
                                    existingRatings[id] = score
                                }
                            }
                        }
                    }
                }
                // 有观看进度或 eps 缺失时，使用用户正篇章节状态纠正分子与分母
                val authenticatedEpisodeIds = allSubjects
                    .filter {
                        it.type == 2 ||
                            it.ep_status > 0 ||
                            !episodeTotals.containsKey(it.subject_id)
                    }
                    .map { it.subject_id }
                    .distinct()
                if (accessToken.isNotBlank() && authenticatedEpisodeIds.isNotEmpty()) {
                    kotlinx.coroutines.coroutineScope {
                        authenticatedEpisodeIds.chunked(8).forEach { batch ->
                            batch.map { id ->
                                async {
                                    runCatchingCancellable {
                                        id to dataSource.episodeCollections(context, id)
                                    }.getOrNull()
                                }
                            }.mapNotNull { it.await() }.forEach { (id, episodes) ->
                                if (episodes.total > 0) episodeTotals[id] = episodes.total
                                val episodeCollections = episodes.data.orEmpty()
                                watchedEpisodeCollections[id] = episodeCollections
                                watchedEpisodeCounts[id] =
                                    episodeCollections.count { it.type == 2 }
                            }
                        }
                    }
                }
                // 未能通过授权接口补齐的条目，使用公开 type=0 章节总数
                val missingEpisodeIds = subjectIds.filterNot(episodeTotals::containsKey)
                if (missingEpisodeIds.isNotEmpty()) {
                    kotlinx.coroutines.coroutineScope {
                        missingEpisodeIds.chunked(8).forEach { batch ->
                            batch.map { id ->
                                async {
                                    runCatchingCancellable {
                                        val total = dataSource.publicEpisodeTotal(id)
                                            .takeIf { it > 0 }
                                        if (total != null) id to total else null
                                    }.getOrNull()
                                }
                            }.mapNotNull { it.await() }.forEach { (id, total) ->
                                episodeTotals[id] = total
                            }
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        ratings = existingRatings.toMap(),
                        episodeTotals = episodeTotals.toMap(),
                        watchedEpisodeCounts = watchedEpisodeCounts.toMap()
                    )
                }
                if (accessToken.isNotBlank() && watchedEpisodeCollections.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        ActivityStats.syncBangumi(
                            context,
                            allSubjects,
                            watchedEpisodeCollections
                        )
                    }
                }
                savePageCache(
                    context,
                    username,
                    allData,
                    existingRatings,
                    episodeTotals,
                    watchedEpisodeCounts
                )
                hasLoaded = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(error = Pair(R.string.general_error, error.message)) }
            } finally {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                isRefreshInProgress = false
            }
        }
    }

    private suspend fun syncCachedWatchingActivity(
        context: Context,
        collections: Map<Int, List<BangumiCollection>>
    ) {
        val watching = collections[2].orEmpty().distinctBy { it.subject_id }
        if (watching.isEmpty()) return

        val episodeCollections = mutableMapOf<Int, List<BangumiUserEpisodeCollection>>()
        kotlinx.coroutines.coroutineScope {
            watching.chunked(8).forEach { batch ->
                batch.map { item ->
                    async {
                        runCatchingCancellable {
                            item.subject_id to dataSource.episodeCollections(
                                context,
                                item.subject_id
                            )
                        }.getOrNull()
                    }
                }.mapNotNull { it.await() }.forEach { (subjectId, page) ->
                    episodeCollections[subjectId] = page.data.orEmpty()
                }
            }
        }
        if (episodeCollections.isEmpty()) return

        withContext(Dispatchers.IO) {
            ActivityStats.syncBangumi(context, watching, episodeCollections)
        }
    }

    private suspend fun savePageCache(
        context: Context,
        username: String,
        collections: Map<Int, List<BangumiCollection>>,
        ratings: Map<Int, Double>,
        episodeTotals: Map<Int, Int>,
        watchedEpisodeCounts: Map<Int, Int>
    ) {
        val snapshot = BangumiPageSnapshot(
            username = username,
            user = _uiState.value.user,
            collections = collections.mapValues { (_, list) -> list.toList() },
            ratings = ratings.toMap(),
            episodeTotals = episodeTotals.toMap(),
            watchedEpisodeCounts = watchedEpisodeCounts.toMap()
        )
        withContext(Dispatchers.IO) {
            BangumiPageCache.save(context, snapshot)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

private data class CollectionBucketResult(
    val bucket: Int,
    val items: List<BangumiCollection>,
    val succeeded: Boolean
)

private fun Map<Int, List<BangumiCollection>>.restoreTagOrders(
    tagOrders: Map<Int, List<String>>
): Map<Int, List<BangumiCollection>> = mapValues { (_, collections) ->
    collections.map { collection ->
        collection.copy(
            tags = BangumiTagOrder.restore(
                collection.tags,
                tagOrders[collection.subject_id]
            )
        )
    }
}
