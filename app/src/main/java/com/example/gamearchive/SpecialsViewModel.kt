package com.example.gamearchive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Request
import org.json.JSONObject

data class MarketGame(
    val id: Int,
    val name: String,
    val imgUrl: String,
    val finalPriceStr: String,
    val originalPriceStr: String?,
    val discount: Int,
    val reviewDesc: String? = null,
    val backupImgUrl: String? = null,
    val priceVal: Double = 0.0,
    val reviewScore: Int = -1
)

data class SpecialsUiState(
    val games: List<MarketGame>? = null,
    val isLoading: Boolean = false,
    val error: Pair<Int, String?>? = null
)

internal interface SpecialsDataSource {
    suspend fun searchPage(start: Int, language: String): String
}

private class NetworkSpecialsDataSource : SpecialsDataSource {
    override suspend fun searchPage(start: Int, language: String): String {
        val url = "${AppConfig.PROXY_URL}search/results/?query&start=$start&count=100" +
            "&dynamic_data=&sort_by=_ASC&specials=1&infinite=1&l=$language" +
            "&cc=cn&category1=998"
        val request = Request.Builder().url(url).build()
        return GameArchiveApp.okHttpClient.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            JSONObject(response.body?.string().orEmpty()).optString("results_html", "")
        }
    }
}

/**
 * 特惠页的"数据管家"。
 * 负责并发抓取打折游戏、解析数据、记住排序和筛选设置。
 * 界面(SpecialsFragment)只管显示，转屏/切页面时数据和设置都不丢。
 */
class SpecialsViewModel internal constructor(
    private val dataSource: SpecialsDataSource = NetworkSpecialsDataSource()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpecialsUiState())
    val uiState: StateFlow<SpecialsUiState> = _uiState.asStateFlow()

    // 抓取到的原始游戏列表

    // 是否正在加载

    /** 出错提示（一次性），Pair<resId, formatArg?> */

    // 用户的筛选与排序设置（Compose State，转屏后保留）
    var isFilteringOwned by mutableStateOf(false)
    var sortMode by mutableIntStateOf(0)
    var priceFilter by mutableIntStateOf(0)   // 0=全部 1=0-10 2=10-50 3=50-100 4=100+

    private var hasLoaded = false
    private var lastLanguage: String? = null

    /** 界面首次进入时调用：已经有数据就不重复加载（语言未变时） */
    fun loadIfNeeded() {
        if (hasLoaded && lastLanguage == LocaleHelper.currentApiLanguage) return
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val list = fetchSpecials()
                _uiState.update {
                    it.copy(
                        games = list,
                        error = if (list.isEmpty()) Pair(R.string.general_no_data, null) else null
                    )
                }
                hasLoaded = true
                lastLanguage = LocaleHelper.currentApiLanguage
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(error = Pair(R.string.general_error, error.message)) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // 并发抓取特惠游戏
    private suspend fun fetchSpecials(): List<MarketGame> = supervisorScope {
        val totalPages = 2

        val deferredResults = (0 until totalPages).map { pageIndex ->
            async {
                // 在每个异步任务内部加 try-catch
                // 这样即使某一页请求超时，也不会导致整个 App 闪退，只会少显示那一页的数据
                try {
                    parseSteamSearchHtml(
                        dataSource.searchPage(
                            start = pageIndex * 100,
                            language = LocaleHelper.currentApiLanguage
                        )
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (e: Exception) {
                    // 如果这一页加载失败 (比如超时)，只打印日志，返回空列表，保全大局
                    android.util.Log.e("SpecialsLoad", "第 $pageIndex 页加载失败: ${e.message}")
                    emptyList<MarketGame>()
                }
            }
        }

        val allBatches = deferredResults.awaitAll()
        allBatches.flatten().distinctBy { it.name }
    }

    /** 错误提示已显示，清空避免重复弹出 */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // 解析HTML数据
    internal fun parseSteamSearchHtml(html: String): List<MarketGame> {
        // 过滤非游戏内容的关键词
        val bannedKeywords = listOf(
            "DLC", "Soundtrack", "原声带", "Artbook", "Upgrade", "升级包",
            "Season Pass", "季票", "Expansion", "扩展包", "Demo", "试玩",
            "Pack", "Content", "Ticket", "Pass", "Skin", "Outfit",
            "Map", "Token", "Coin", "Wallpaper", "OST",
            "Deluxe", "Edition", "Bundle", "Collection", "Master", "Remastered",
            "Gold", "Ultimate", "Premium", "组合包", "纪念版",
            "Annual Pass", "Starter Pack", "Booster Pack", "Add-On"
        )
        return parseSteamSearchRows(html)
            .asSequence()
            .filterNot { row ->
                bannedKeywords.any { row.title.contains(it, ignoreCase = true) }
            }
            .distinctBy { it.title }
            .map { row ->
                MarketGame(
                    id = row.appId,
                    name = row.title,
                    imgUrl = "https://cdn.cloudflare.steamstatic.com/steam/apps/${row.appId}/header.jpg",
                    finalPriceStr = row.finalPrice.ifBlank { "¥ --" },
                    originalPriceStr = row.originalPrice,
                    discount = row.discount,
                    backupImgUrl = row.imageUrl,
                    priceVal = row.priceValue,
                    reviewScore = row.reviewScore
                )
            }
            .toList()
    }
}
