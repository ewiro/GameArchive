package com.example.gamearchive

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap

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

    // Steam 社区公开资料装扮（不影响库存主流程）
    private val _profileDecor = MutableLiveData<SteamProfileDecor?>()
    val profileDecor: LiveData<SteamProfileDecor?> = _profileDecor
    private var profileDecorSteamId: String? = null

    // Steam 等级
    private val _level = MutableLiveData<Int>()
    val level: LiveData<Int> = _level

    // 价格表：游戏ID -> 价格文字
    val priceMap = mutableMapOf<Int, String>()

    private val reviewStates = ConcurrentHashMap<String, MutableLiveData<String?>>()
    private val reviewSemaphore = Semaphore(4)

    // 是否正在加载
    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    /** 出错提示（一次性），Pair<resId, formatArg?> */
    private val _error = MutableLiveData<Pair<Int, String?>?>()
    val error: LiveData<Pair<Int, String?>?> = _error

    // 数据是否已经加载过（避免重复联网）
    private var hasLoaded = false
    // 上次加载时使用的语言，切换语言时强制重新抓取
    private var lastLanguage: String? = null

    /**
     * 返回单个游戏的评价状态。请求按 AppID/语言去重并限制并发，生命周期跟随 ViewModel。
     */
    fun reviewScore(context: Context, appId: Int): LiveData<String?> {
        val language = LocaleHelper.currentApiLanguage
        val key = "${appId}_$language"
        return reviewStates.getOrPut(key) {
            MutableLiveData<String?>().also { state ->
                val appContext = context.applicationContext
                viewModelScope.launch(Dispatchers.IO) {
                    val prefs = appContext.getSharedPreferences(
                        "steam_reviews_cache",
                        Context.MODE_PRIVATE
                    )
                    val cacheKey = "review_$key"
                    val cached = readTimedCache(
                        prefs.getString(cacheKey, null),
                        30L * 24 * 60 * 60 * 1000
                    )
                    if (cached != null) {
                        state.postValue(cached)
                        return@launch
                    }
                    reviewSemaphore.withPermit {
                        try {
                            val summary = GameArchiveApp.apiService
                                .getGameReviews(appId, l = language)
                                .query_summary
                            if (summary != null && summary.total_reviews > 0) {
                                val rate = (
                                    summary.total_positive.toDouble() /
                                        summary.total_reviews * 100
                                    ).toInt()
                                val text = appContext.getString(
                                    R.string.review_score_format,
                                    rate
                                )
                                prefs.edit().putString(
                                    cacheKey,
                                    "$text|${System.currentTimeMillis()}"
                                ).apply()
                                state.postValue(text)
                            }
                        } catch (_: Exception) {
                            // 卡片评价属于附加信息，失败时保持为空。
                        }
                    }
                }
            }
        }
    }

    /** 界面首次进入时调用：已经有数据就不重复加载（语言未变时） */
    fun loadIfNeeded(apiKey: String, steamId: String, context: android.content.Context? = null) {
        if (hasLoaded && lastLanguage == LocaleHelper.currentApiLanguage) return
        refresh(apiKey, steamId, context)
    }

    fun loadProfileDecor(
        context: Context,
        steamId: String,
        forceRefresh: Boolean = false
    ) {
        if (!forceRefresh && profileDecorSteamId == steamId && _profileDecor.value != null) return
        if (profileDecorSteamId != steamId) {
            profileDecorSteamId = steamId
            _profileDecor.value = null
        }
        val appContext = context.applicationContext
        viewModelScope.launch {
            val decor = SteamProfileDecorRepository.load(
                context = appContext,
                steamId = steamId,
                forceRefresh = forceRefresh
            )
            if (profileDecorSteamId == steamId) {
                _profileDecor.value = decor
            }
        }
    }

    /** 下拉刷新或首次加载 */
    fun refresh(apiKey: String, steamId: String, context: android.content.Context? = null) {
        if (apiKey.isEmpty() || steamId.isEmpty()) {
            _error.value = Pair(R.string.general_please_login, null)
            return
        }

        viewModelScope.launch {
            _loading.value = true
            try {
                supervisorScope {
                    val playerDeferred = async {
                        runCatching {
                            GameArchiveApp.apiService
                                .getPlayerSummaries(apiKey, steamId)
                                .response.players
                                .firstOrNull()
                        }.getOrNull()
                    }
                    val levelDeferred = async {
                        runCatching {
                            GameArchiveApp.apiService
                                .getSteamLevel(apiKey, steamId)
                                .response.player_level ?: 0
                        }.getOrDefault(0)
                    }
                    // 收集所有账号
                    val allAccounts = if (context != null) {
                        val extra = UserPrefs.getAdditionalAccounts(context)
                        listOf(Pair(steamId, apiKey)) + extra
                    } else {
                        listOf(Pair(steamId, apiKey))
                    }

                    // 黑名单
                    val blackListIds = setOf(3081410)

                    // 并发拉取所有账号的库存
                    val allGamesDeferred = allAccounts.map { (sid, key) ->
                        async { fetchGamesForAccount(key, sid, blackListIds) }
                    }

                    // 合并去重（按appid）
                    val seen = mutableSetOf<Int>()
                    val merged = mutableListOf<GameInfo>()
                    val accountGameResults = allGamesDeferred.map { it.await() }
                    val accountGames = accountGameResults.filterNotNull()
                    for (games in accountGames) {
                        for (g in games) {
                            if (seen.add(g.appid)) merged.add(g)
                        }
                    }

                    // 统计使用所有账号的累计时长，同一 AppID 先求和；库存展示仍保持现有去重逻辑
                    if (context != null && accountGames.size == allAccounts.size) {
                        withContext(Dispatchers.IO) {
                            ActivityStats.syncSteam(context, accountGames.flatten())
                        }
                    }

                    // 更新全局拥有的游戏ID列表
                    MainActivity.ownedGameIds.clear()
                    MainActivity.ownedGameIds.addAll(merged.map { it.appid })

                    // 玩家信息（仅主账号）
                    val priceDeferred = async {
                        fetchBatchPrices(
                            merged.sortedByDescending { it.playtime_forever }.take(20)
                        )
                    }
                    val playerInfo = playerDeferred.await()
                    if (context != null && playerInfo != null) {
                        withContext(Dispatchers.IO) {
                            UserPrefs.saveSteamNickname(
                                context,
                                steamId,
                                playerInfo.personaname
                            )
                        }
                    }

                    // Steam 等级（仅主账号）
                    val playerLevel = levelDeferred.await()

                    // 批量获取前20个游戏的价格
                    priceDeferred.await()

                    _games.value = merged
                    _player.value = playerInfo
                    _level.value = playerLevel
                    if (
                        context != null &&
                        playerInfo != null &&
                        UserPrefs.isShowProfile(context)
                    ) {
                        loadProfileDecor(
                            context = context,
                            steamId = playerInfo.steamid,
                            forceRefresh = hasLoaded
                        )
                    }
                    hasLoaded = true
                    lastLanguage = LocaleHelper.currentApiLanguage
                }
            } catch (e: Exception) {
                _error.value = when {
                    e is HttpException && e.code() == 403 ->
                        Pair(R.string.general_api_key_invalid, null)
                    e is HttpException ->
                        Pair(R.string.general_network_http, e.code().toString())
                    else ->
                        Pair(R.string.general_error, e.message)
                }
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun fetchGamesForAccount(
        apiKey: String,
        steamId: String,
        blackListIds: Set<Int>
    ): List<GameInfo>? {
        return try {
            val gameRes = GameArchiveApp.apiService.getOwnedGames(apiKey, steamId)
            gameRes.response.games.filter { !blackListIds.contains(it.appid) }
        } catch (e: Exception) { null }
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
                    priceMap[id] = "Free / Unknown"
                }
            }
        } catch (e: Exception) { }
    }

    /** 错误提示已显示，清空避免重复弹出 */
    fun clearError() {
        _error.value = null
    }

    private fun readTimedCache(entry: String?, ttlMs: Long): String? {
        entry ?: return null
        val parts = entry.split("|", limit = 2)
        if (parts.size != 2) return null
        val timestamp = parts[1].toLongOrNull() ?: return null
        return parts[0].takeIf { System.currentTimeMillis() - timestamp <= ttlMs }
    }
}
