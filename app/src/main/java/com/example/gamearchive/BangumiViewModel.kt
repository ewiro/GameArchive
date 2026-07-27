package com.example.gamearchive

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bangumi（番组计划）动漫收藏页的"数据管家"。
 * 负责按收藏类型（想看/看过/在看等）联网拉取数据。
 */
class BangumiViewModel : ViewModel() {

    // 用户信息
    private val _user = MutableLiveData<BangumiUser>()
    val user: LiveData<BangumiUser> = _user

    // 分组后的收藏: type(1-5) -> List<BangumiCollection>
    private val _collections = MutableLiveData<Map<Int, List<BangumiCollection>>>()
    val collections: LiveData<Map<Int, List<BangumiCollection>>> = _collections

    // subject_id → rating Map（从 /v0/subjects/{id} 补拉）
    private val _ratings = MutableLiveData<Map<Int, Any?>>()
    val ratings: LiveData<Map<Int, Any?>> = _ratings

    // subject_id → 正篇章节总数（eps 缺失时由 type=0 章节接口补拉）
    private val _episodeTotals = MutableLiveData<Map<Int, Int>>()
    val episodeTotals: LiveData<Map<Int, Int>> = _episodeTotals

    // subject_id → 仅正篇中已看章节数
    private val _watchedEpisodeCounts = MutableLiveData<Map<Int, Int>>()
    val watchedEpisodeCounts: LiveData<Map<Int, Int>> = _watchedEpisodeCounts

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<Pair<Int, String?>?>()
    val error: LiveData<Pair<Int, String?>?> = _error

    private var hasLoaded = false
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
        if (hasLoaded || isRefreshInProgress) return
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                BangumiPageCache.load(context, username)
            }
            if (cached != null && _collections.value == null) {
                cached.user?.let { _user.value = it }
                _collections.value = cached.collections
                _ratings.value = cached.ratings
                _episodeTotals.value = cached.episodeTotals
                _watchedEpisodeCounts.value = cached.watchedEpisodeCounts
                _loading.value = false
            }
        }
        refresh(username, accessToken, context)
    }

    fun refresh(username: String, accessToken: String, context: Context) {
        if (isRefreshInProgress) return
        if (username.isBlank()) {
            _error.value = Pair(R.string.bangumi_no_username, null)
            return
        }
        isRefreshInProgress = true
        viewModelScope.launch {
            _loading.value = true
            try {
                // 并发：用户信息 + 所有分类收藏同时拉取
                val userDeferred = async {
                    try { GameArchiveApp.bgmService.getUserInfo(username) } catch (_: Exception) { null }
                }
                // 5 个分类并发拉取
                val typeDeferreds = (1..5).map { type ->
                    async {
                        val bucket = when (type) { 2 -> 3; 3 -> 2; else -> type }
                        runCatching {
                            val list = mutableListOf<BangumiCollection>()
                            var offset = 0
                            while (true) {
                                val page = GameArchiveApp.bgmService.getUserCollections(
                                    username, subjectType = 2, collectionType = type,
                                    limit = 50, offset = offset
                                )
                                page.data?.forEach { item ->
                                    val t = when (item.type) { 2 -> 3; 3 -> 2; else -> item.type }
                                    list.add(item.copy(type = t))
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
                    val earlyData = _collections.value.orEmpty()
                        .mapValuesTo(mutableMapOf()) { (_, list) -> list.toMutableList() }
                    if (watchingResult.items.isEmpty()) {
                        earlyData.remove(watchingResult.bucket)
                    } else {
                        earlyData[watchingResult.bucket] =
                            watchingResult.items.toMutableList()
                    }
                    _collections.value = earlyData
                    _loading.value = false
                }
                val bucketResults = typeDeferreds.awaitAll()
                if (bucketResults.none { it.succeeded }) {
                    _error.value = Pair(R.string.bangumi_collection_load_failed, null)
                    return@launch
                }
                val allData = _collections.value.orEmpty()
                    .mapValuesTo(mutableMapOf()) { (_, list) -> list.toMutableList() }
                bucketResults.filter { it.succeeded }.forEach { result ->
                    if (result.items.isEmpty()) {
                        allData.remove(result.bucket)
                    } else {
                        allData[result.bucket] = result.items.toMutableList()
                    }
                }
                _collections.value = allData
                _loading.value = false
                userDeferred.await()?.let { _user.value = it }

                // 批量拉取 subject 详情评分（并发 8 个一组）
                val existingRatings = _ratings.value.orEmpty().toMutableMap()
                val episodeTotals = _episodeTotals.value.orEmpty().toMutableMap()
                val watchedEpisodeCounts =
                    _watchedEpisodeCounts.value.orEmpty().toMutableMap()
                val allSubjects = allData.values.flatten()
                // 先收集已有评分的（collections API 可能部分返回了）
                for (item in allSubjects) {
                    val r = item.subject?.rating
                    if (r != null) existingRatings[item.subject_id] = r
                    val total = item.subject?.eps?.takeIf { it > 0 }
                    if (total != null) episodeTotals[item.subject_id] = total
                }
                _ratings.value = existingRatings.toMap()
                _episodeTotals.value = episodeTotals.toMap()
                _watchedEpisodeCounts.value = watchedEpisodeCounts.toMap()
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
                                    try {
                                        id to GameArchiveApp.bgmService.getSubject(id)
                                    } catch (_: Exception) { null }
                                }
                            }.mapNotNull { it.await() }.forEach { (id, detail) ->
                                if (detail.rating != null) existingRatings[id] = detail.rating
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
                    val authenticatedService =
                        GameArchiveApp.createAuthenticatedBgmService(accessToken)
                    kotlinx.coroutines.coroutineScope {
                        authenticatedEpisodeIds.chunked(8).forEach { batch ->
                            batch.map { id ->
                                async {
                                    try {
                                        id to authenticatedService.getEpisodeCollections(id)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            }.mapNotNull { it.await() }.forEach { (id, episodes) ->
                                if (episodes.total > 0) episodeTotals[id] = episodes.total
                                watchedEpisodeCounts[id] =
                                    episodes.data.orEmpty().count { it.type == 2 }
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
                                    try {
                                        val total = GameArchiveApp.bgmService
                                            .getSubjectEpisodes(id)
                                            .total
                                            .takeIf { it > 0 }
                                        if (total != null) id to total else null
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            }.mapNotNull { it.await() }.forEach { (id, total) ->
                                episodeTotals[id] = total
                            }
                        }
                    }
                }
                _ratings.value = existingRatings
                _episodeTotals.value = episodeTotals
                _watchedEpisodeCounts.value = watchedEpisodeCounts
                if (accessToken.isNotBlank() && watchedEpisodeCounts.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        ActivityStats.syncBangumi(context, allSubjects, watchedEpisodeCounts)
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
            } catch (e: Exception) {
                _error.value = Pair(R.string.general_error, e.message)
            } finally {
                _loading.value = false
                isRefreshInProgress = false
            }
        }
    }

    private suspend fun savePageCache(
        context: Context,
        username: String,
        collections: Map<Int, List<BangumiCollection>>,
        ratings: Map<Int, Any?>,
        episodeTotals: Map<Int, Int>,
        watchedEpisodeCounts: Map<Int, Int>
    ) {
        val snapshot = BangumiPageSnapshot(
            username = username,
            user = _user.value,
            collections = collections.mapValues { (_, list) -> list.toList() },
            ratings = ratings.toMap(),
            episodeTotals = episodeTotals.toMap(),
            watchedEpisodeCounts = watchedEpisodeCounts.toMap()
        )
        withContext(Dispatchers.IO) {
            BangumiPageCache.save(context, snapshot)
        }
    }

    fun clearError() { _error.value = null }
}

private data class CollectionBucketResult(
    val bucket: Int,
    val items: List<BangumiCollection>,
    val succeeded: Boolean
)
