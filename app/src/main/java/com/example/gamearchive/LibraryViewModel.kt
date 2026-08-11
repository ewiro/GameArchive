package com.example.gamearchive

import androidx.core.content.edit
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap

data class LibraryUiState(
    val games: List<GameInfo>? = null,
    val player: PlayerInfo? = null,
    val profileDecor: SteamProfileDecor? = null,
    val level: Int? = null,
    val prices: Map<Int, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: Pair<Int, String?>? = null
)

internal interface LibraryDataSource {
    suspend fun player(apiKey: String, steamId: String): PlayerInfo?
    suspend fun level(apiKey: String, steamId: String): Int
    suspend fun games(apiKey: String, steamId: String): List<GameInfo>
    suspend fun prices(appIds: String, language: String): Map<String, StoreAppDetails>
    suspend fun reviews(appId: Int, language: String): ReviewResponse
}

private class NetworkLibraryDataSource : LibraryDataSource {
    override suspend fun player(apiKey: String, steamId: String) =
        GameArchiveApp.apiService.getPlayerSummaries(apiKey, steamId)
            .response.players.firstOrNull()

    override suspend fun level(apiKey: String, steamId: String) =
        GameArchiveApp.apiService.getSteamLevel(apiKey, steamId).response.player_level ?: 0

    override suspend fun games(apiKey: String, steamId: String) =
        GameArchiveApp.apiService.getOwnedGames(apiKey, steamId).response.games

    override suspend fun prices(appIds: String, language: String) =
        GameArchiveApp.apiService.getGamePrices(appIds, l = language)

    override suspend fun reviews(appId: Int, language: String) =
        GameArchiveApp.apiService.getGameReviews(appId, l = language)
}

/**
 * 库存页的"数据管家"。
 * 负责联网拉取游戏库存、玩家信息、价格，并把数据存起来。
 * 界面(LibraryFragment)只负责显示，转屏/切页面时这里的数据不会丢。
 */
class LibraryViewModel internal constructor(
    private val dataSource: LibraryDataSource = NetworkLibraryDataSource()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // 游戏列表

    // 玩家个人资料

    // Steam 社区公开资料装扮（不影响库存主流程）
    private var profileDecorSteamId: String? = null

    // Steam 等级

    // 价格表：游戏ID -> 价格文字
    private val reviewStates = ConcurrentHashMap<String, MutableStateFlow<String?>>()
    private val reviewSemaphore = Semaphore(4)

    // 是否正在加载

    /** 出错提示（一次性），Pair<resId, formatArg?> */

    // 数据是否已经加载过（避免重复联网）
    private var hasLoaded = false
    // 上次加载时使用的语言，切换语言时强制重新抓取
    private var lastLanguage: String? = null

    /**
     * 返回单个游戏的评价状态。请求按 AppID/语言去重并限制并发，生命周期跟随 ViewModel。
     */
    fun reviewScore(context: Context, appId: Int): StateFlow<String?> {
        val language = LocaleHelper.currentApiLanguage
        val key = "${appId}_$language"
        return reviewStates.getOrPut(key) {
            MutableStateFlow<String?>(null).also { state ->
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
                        state.value = cached
                        return@launch
                    }
                    reviewSemaphore.withPermit {
                        try {
                            val summary = dataSource.reviews(appId, language).query_summary
                            if (summary != null && summary.total_reviews > 0) {
                                val rate = (
                                    summary.total_positive.toDouble() /
                                        summary.total_reviews * 100
                                    ).toInt()
                                val text = appContext.getString(
                                    R.string.review_score_format,
                                    rate
                                )
                                prefs.edit {putString(
                                        cacheKey,
                                        "$text|${System.currentTimeMillis()}"
                                )}
                                state.value = text
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            android.util.Log.w("LibraryViewModel", "Review lookup failed", error)
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
        if (!forceRefresh && profileDecorSteamId == steamId && _uiState.value.profileDecor != null) return
        if (profileDecorSteamId != steamId) {
            profileDecorSteamId = steamId
            _uiState.update { it.copy(profileDecor = null) }
        }
        val appContext = context.applicationContext
        viewModelScope.launch {
            val decor = SteamProfileDecorRepository.load(
                context = appContext,
                steamId = steamId,
                forceRefresh = forceRefresh
            )
            if (profileDecorSteamId == steamId) {
                _uiState.update { it.copy(profileDecor = decor) }
            }
        }
    }

    /** 下拉刷新或首次加载 */
    fun refresh(apiKey: String, steamId: String, context: android.content.Context? = null) {
        if (apiKey.isEmpty() || steamId.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = Pair(R.string.general_please_login, null)
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                supervisorScope {
                    val playerDeferred = async {
                        runCatchingCancellable {
                            dataSource.player(apiKey, steamId)
                        }.getOrNull()
                    }
                    val levelDeferred = async {
                        runCatchingCancellable {
                            dataSource.level(apiKey, steamId)
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
                    val accountGameResults = allGamesDeferred.map { it.await() }
                    val accountGames = accountGameResults.filterNotNull()
                    val merged = mergeOwnedGames(accountGames)

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
                    val prices = priceDeferred.await()

                    _uiState.update {
                        it.copy(
                            games = merged,
                            player = playerInfo,
                            level = playerLevel,
                            prices = prices
                        )
                    }
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = when {
                    error is HttpException && error.code() == 403 ->
                        Pair(R.string.general_api_key_invalid, null)
                    error is HttpException ->
                        Pair(R.string.general_network_http, error.code().toString())
                    else ->
                        Pair(R.string.general_error, error.message)
                }
                _uiState.update { it.copy(error = message) }
                android.util.Log.e("LibraryViewModel", "Library refresh failed", error)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun fetchGamesForAccount(
        apiKey: String,
        steamId: String,
        blackListIds: Set<Int>
    ): List<GameInfo>? {
        return try {
            dataSource.games(apiKey, steamId).filter { !blackListIds.contains(it.appid) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("LibraryViewModel", "Inventory lookup failed", error)
            null
        }
    }

    private suspend fun fetchBatchPrices(games: List<GameInfo>): Map<Int, String> {
        if (games.isEmpty()) return emptyMap()
        return try {
            val ids = games.joinToString(",") { it.appid.toString() }
            val response = dataSource.prices(ids, LocaleHelper.currentApiLanguage)

            buildMap {
                for ((idStr, details) in response) {
                    val id = idStr.toIntOrNull() ?: continue
                    put(
                        id,
                        if (details.success && details.data?.price_overview != null) {
                            details.data.price_overview.final_formatted ?: "¥ --"
                        } else {
                            "Free / Unknown"
                        }
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("LibraryViewModel", "Batch price lookup failed", error)
            emptyMap()
        }
    }

    /** 错误提示已显示，清空避免重复弹出 */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun readTimedCache(entry: String?, ttlMs: Long): String? {
        entry ?: return null
        val parts = entry.split("|", limit = 2)
        if (parts.size != 2) return null
        val timestamp = parts[1].toLongOrNull() ?: return null
        return parts[0].takeIf { System.currentTimeMillis() - timestamp <= ttlMs }
    }
}

internal fun mergeOwnedGames(accountGames: List<List<GameInfo>>): List<GameInfo> {
    val gamesById = LinkedHashMap<Int, GameInfo>()
    accountGames.forEach { games ->
        games.forEach { game -> gamesById.putIfAbsent(game.appid, game) }
    }
    return gamesById.values.toList()
}
