package com.example.gamearchive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/**
 * 特惠页的"数据管家"。
 * 负责并发抓取打折游戏、解析数据、记住排序和筛选设置。
 * 界面(SpecialsFragment)只管显示，转屏/切页面时数据和设置都不丢。
 */
class SpecialsViewModel : ViewModel() {

    // 抓取到的原始游戏列表
    private val _rawList = MutableLiveData<List<MarketGame>>()
    val rawList: LiveData<List<MarketGame>> = _rawList

    // 是否正在加载
    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    /** 出错提示（一次性），Pair<resId, formatArg?> */
    private val _error = MutableLiveData<Pair<Int, String?>?>()
    val error: LiveData<Pair<Int, String?>?> = _error

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
            _loading.value = true
            try {
                val list = withContext(Dispatchers.IO) { fetchSpecials() }
                _rawList.value = list
                if (list.isEmpty()) _error.value = Pair(R.string.general_no_data, null)
                hasLoaded = true
                lastLanguage = LocaleHelper.currentApiLanguage
            } catch (e: Exception) {
                _error.value = Pair(R.string.general_error, e.message)
            } finally {
                _loading.value = false
            }
        }
    }

    // 并发抓取特惠游戏
    private suspend fun fetchSpecials(): List<MarketGame> {
        val totalPages = 2

        val deferredResults = (0 until totalPages).map { pageIndex ->
            viewModelScope.async(Dispatchers.IO) {
                // 在每个异步任务内部加 try-catch
                // 这样即使某一页请求超时，也不会导致整个 App 闪退，只会少显示那一页的数据
                try {
                    val start = pageIndex * 100
                    val url = "${AppConfig.PROXY_URL}search/results/?query&start=$start&count=100&dynamic_data=&sort_by=_ASC&specials=1&infinite=1&l=${LocaleHelper.currentApiLanguage}&cc=cn&category1=998"

                    val request = Request.Builder().url(url).build()
                    val response = GameArchiveApp.okHttpClient.newCall(request).execute()
                    val jsonStr = response.body?.string() ?: "{}"
                    val jsonObj = JSONObject(jsonStr)
                    val html = jsonObj.optString("results_html", "")

                    parseSteamSearchHtml(html)
                } catch (e: Exception) {
                    // 如果这一页加载失败 (比如超时)，只打印日志，返回空列表，保全大局
                    android.util.Log.e("SpecialsLoad", "第 $pageIndex 页加载失败: ${e.message}")
                    emptyList<MarketGame>()
                }
            }
        }

        val allBatches = deferredResults.awaitAll()
        return allBatches.flatten().distinctBy { it.name }
    }

    /** 错误提示已显示，清空避免重复弹出 */
    fun clearError() {
        _error.value = null
    }

    // 解析HTML数据
    private fun parseSteamSearchHtml(html: String): List<MarketGame> {
        val list = mutableListOf<MarketGame>()
        val localUnique = mutableSetOf<String>()

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

        val rows = html.split("<a href=")

        for (row in rows) {
            try {
                val nameMatch = Regex("<span class=\"title\">(.*?)</span>").find(row)
                val name = nameMatch?.groupValues?.get(1)?.trim() ?: "Unknown"

                if (bannedKeywords.any { name.contains(it, ignoreCase = true) }) continue
                if (localUnique.contains(name)) continue
                localUnique.add(name)

                val appIdMatch = Regex("data-ds-appid=\"([0-9,]+)\"").find(row)

                var id = appIdMatch?.groupValues?.get(1)?.split(",")?.first()?.toIntOrNull() ?: 0
                val isBundle = id == 0
                if (isBundle) continue // 仅保留游戏本体

                val rawImgMatch = Regex("src=\"(https://[^\"]+?\\.jpg[^\"]*)\"").find(row)
                val rawImgUrl = rawImgMatch?.groupValues?.get(1) ?: ""
                val standardImgUrl = "https://cdn.cloudflare.steamstatic.com/steam/apps/$id/header.jpg"

                val discountMatch = Regex("-([0-9]+)%").find(row)
                val discount = discountMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                val priceMatch = Regex("discount_final_price\">([^<]+)</div>").find(row)
                val priceStr = priceMatch?.groupValues?.get(1)?.trim() ?: "¥ --"
                val priceVal = try { priceStr.replace(Regex("[^0-9.]"), "").toDouble() } catch (e: Exception) { 0.0 }

                val originMatch = Regex("discount_original_price\">([^<]+)</div>").find(row)
                val originStr = originMatch?.groupValues?.get(1)?.trim() ?: ""

                // 好评率解析
                var reviewScore = -1
                val tooltipMatch = Regex("data-tooltip-html=\"([^\"]+)\"").find(row)
                if (tooltipMatch != null) {
                    val content = tooltipMatch.groupValues[1]
                    val scoreMatch = Regex("([0-9]{1,3})%").find(content)
                    if (scoreMatch != null) {
                        reviewScore = scoreMatch.groupValues[1].toIntOrNull() ?: -1
                    }
                }

                list.add(MarketGame(id, name, rawImgUrl, priceStr, originStr, discount, null, standardImgUrl, priceVal, reviewScore))

            } catch (e: Exception) {}
        }
        return list
    }
}
