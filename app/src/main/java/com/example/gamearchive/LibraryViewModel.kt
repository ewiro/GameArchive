package com.example.gamearchive

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * 库存页的"数据管家"。
 * 负责联网拉取游戏库存、玩家信息、价格，并把数据存起来。
 * 界面(LibraryFragment)只负责显示，转屏/切页面时这里的数据不会丢。
 */
class LibraryViewModel : ViewModel() {

    // 游戏列表
    private val _games = MutableLiveData<List<GameInfo>>()
    val games: LiveData<List<GameInfo>> = _games

    // 玩家个人资料
    private val _player = MutableLiveData<PlayerInfo?>()
    val player: LiveData<PlayerInfo?> = _player

    // Steam 等级
    private val _level = MutableLiveData<Int>()
    val level: LiveData<Int> = _level

    // 价格表：游戏ID -> 价格文字
    val priceMap = mutableMapOf<Int, String>()

    // 是否正在加载
    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    // 出错提示文字（一次性）
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // 数据是否已经加载过（避免重复联网）
    private var hasLoaded = false
    // 上次加载时使用的语言，切换语言时强制重新抓取
    private var lastLanguage: String? = null

    /** 界面首次进入时调用：已经有数据就不重复加载（语言未变时） */
    fun loadIfNeeded(apiKey: String, steamId: String) {
        if (hasLoaded && lastLanguage == LocaleHelper.currentApiLanguage) return
        refresh(apiKey, steamId)
    }

    /** 下拉刷新或首次加载 */
    fun refresh(apiKey: String, steamId: String) {
        if (apiKey.isEmpty() || steamId.isEmpty()) {
            _error.value = "Please login first"
            return
        }

        viewModelScope.launch {
            _loading.value = true
            try {
                // 三个请求并发发出
                val gameDeferred = async { GameArchiveApp.apiService.getOwnedGames(apiKey, steamId) }
                val userDeferred = async { GameArchiveApp.apiService.getPlayerSummaries(apiKey, steamId) }
                val levelDeferred = async {
                    try {
                        GameArchiveApp.apiService.getSteamLevel(apiKey, steamId)
                    } catch (e: Exception) { null } // 等级失败不影响其他
                }

                // 库存游戏 + 黑名单过滤
                val gameRes = gameDeferred.await()
                // 黑名单：战地6测试版 (Steam 已下架其数据，但库存接口仍返回，强制隐藏)
                val blackListIds = setOf(3081410)
                val gameList = gameRes.response.games.filter { game ->
                    !blackListIds.contains(game.appid)
                }

                // 更新全局拥有的游戏ID列表
                MainActivity.ownedGameIds.clear()
                MainActivity.ownedGameIds.addAll(gameList.map { it.appid })

                // 玩家信息
                val userRes = userDeferred.await()
                val playerInfo = if (userRes.response.players.isNotEmpty()) userRes.response.players[0] else null

                // Steam 等级
                val playerLevel = levelDeferred.await()?.response?.player_level ?: 0

                // 批量获取前20个游戏的价格
                fetchBatchPrices(gameList.sortedByDescending { it.playtime_forever }.take(20))

                _games.value = gameList
                _player.value = playerInfo
                _level.value = playerLevel
                hasLoaded = true
                lastLanguage = LocaleHelper.currentApiLanguage
            } catch (e: Exception) {
                _error.value = "Load failed: ${e.message}"
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun fetchBatchPrices(games: List<GameInfo>) {
        if (games.isEmpty()) return
        try {
            val ids = games.joinToString(",") { it.appid.toString() }
            val response = GameArchiveApp.apiService.getGamePrices(ids, l = LocaleHelper.currentApiLanguage)

            for ((idStr, details) in response) {
                val id = idStr.toInt()
                if (details.success && details.data?.price_overview != null) {
                    priceMap[id] = details.data.price_overview.final_formatted ?: "¥ --"
                } else {
                    priceMap[id] = "Free/Unknown"
                }
            }
        } catch (e: Exception) { }
    }

    /** 错误提示已显示，清空避免重复弹出 */
    fun clearError() {
        _error.value = null
    }
}
