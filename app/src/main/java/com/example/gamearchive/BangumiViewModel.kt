package com.example.gamearchive

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

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

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<Pair<Int, String?>?>()
    val error: LiveData<Pair<Int, String?>?> = _error

    private var hasLoaded = false

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

    fun loadIfNeeded(username: String) {
        if (hasLoaded) return
        refresh(username)
    }

    fun refresh(username: String) {
        if (username.isBlank()) {
            _error.value = Pair(R.string.bangumi_no_username, null)
            return
        }
        viewModelScope.launch {
            _loading.value = true
            try {
                // 并发：用户信息 + 所有分类收藏同时拉取
                val userDeferred = viewModelScope.async {
                    try { GameArchiveApp.bgmService.getUserInfo(username) } catch (_: Exception) { null }
                }
                // 5 个分类并发拉取
                val typeDeferreds = (1..5).map { type ->
                    viewModelScope.async {
                        try {
                            val bucket = when (type) { 2 -> 3; 3 -> 2; else -> type }
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
                            if (list.isNotEmpty()) bucket to list else null
                        } catch (_: Exception) { null }
                    }
                }
                val allData = mutableMapOf<Int, MutableList<BangumiCollection>>()
                typeDeferreds.awaitAll().filterNotNull().forEach { (bucket, list) ->
                    allData[bucket] = list
                }
                _user.value = userDeferred.await()
                _collections.value = allData
                // 批量拉取 subject 详情评分（并发 8 个一组）
                val existingRatings = mutableMapOf<Int, Any?>()
                val allSubjects = allData.values.flatten()
                // 先收集已有评分的（collections API 可能部分返回了）
                for (item in allSubjects) {
                    val r = item.subject?.rating
                    if (r != null) existingRatings[item.subject_id] = r
                }
                // 补拉缺失的
                val missingIds = allSubjects.filter { it.subject?.rating == null }.map { it.subject_id }.distinct()
                if (missingIds.isNotEmpty()) {
                    kotlinx.coroutines.coroutineScope {
                        missingIds.chunked(8).forEach { batch ->
                            batch.map { id ->
                                async {
                                    try {
                                        val detail = GameArchiveApp.bgmService.getSubject(id)
                                        if (detail.rating != null) id to detail.rating else null
                                    } catch (_: Exception) { null }
                                }
                            }.mapNotNull { it.await() }.forEach { (id, r) -> existingRatings[id] = r }
                        }
                    }
                }
                _ratings.value = existingRatings
                hasLoaded = true
            } catch (e: Exception) {
                _error.value = Pair(R.string.general_error, e.message)
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }
}
