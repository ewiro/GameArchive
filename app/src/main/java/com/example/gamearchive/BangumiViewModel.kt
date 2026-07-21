package com.example.gamearchive

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
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

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<Pair<Int, String?>?>()
    val error: LiveData<Pair<Int, String?>?> = _error

    private var hasLoaded = false

    /** 收藏类型 → 中文名 */
    companion object {
        val typeNames = mapOf(
            1 to R.string.bangumi_wish,
            2 to R.string.bangumi_done,
            3 to R.string.bangumi_doing,
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
                // 并发：用户信息 + 各分类收藏
                val userDeferred = viewModelScope.async {
                    try { GameArchiveApp.bgmService.getUserInfo(username) } catch (_: Exception) { null }
                }
                val allData = mutableMapOf<Int, MutableList<BangumiCollection>>()
                // 逐个 type 拉取
                for (type in 1..5) {
                    try {
                        val page = GameArchiveApp.bgmService.getUserCollections(username, subjectType = 2, collectionType = type)
                        if (!page.data.isNullOrEmpty()) {
                            allData[type] = page.data.toMutableList()
                        }
                    } catch (_: Exception) { /* 某个分类为空时继续 */ }
                }
                _user.value = userDeferred.await()
                _collections.value = allData
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
