package com.example.gamearchive

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class SpecialsFragment : Fragment() {

    private val PROXY_URL = "https://api.steam-tracker-proxy.cyou/"

    private lateinit var rv: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var adapter: MarketAdapter
    private lateinit var btnSort: MaterialButton
    private lateinit var topBar: View

    private var rawList = listOf<MarketGame>()

    private var isFilteringOwned = false
    private var sortMode = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_specials, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rv = view.findViewById(R.id.rvSpecials)
        progress = view.findViewById(R.id.pbSpecials)
        btnSort = view.findViewById(R.id.btnSort)
        topBar = view.findViewById(R.id.topBarContainer)

        rv.layoutManager = LinearLayoutManager(context)
        rv.setItemViewCacheSize(50)
        rv.setHasFixedSize(true)
        rv.itemAnimator = null

        btnSort.iconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK)

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val mainActivity = activity as? MainActivity ?: return
                if (dy > 10) {
                    mainActivity.setBottomNavVisibility(false)
                    animateTopBar(false)
                } else if (dy < -10) {
                    mainActivity.setBottomNavVisibility(true)
                    animateTopBar(true)
                }
            }
        })

        btnSort.setOnClickListener { showSortAndFilterDialog() }

        loadSpecialsViaSearch()
    }

    private fun animateTopBar(visible: Boolean) {
        val targetY = if (visible) 0f else -topBar.height.toFloat()
        if (topBar.translationY != targetY) {
            topBar.animate().translationY(targetY).setDuration(200).start()
        }
    }

    // 显示排序和筛选弹窗
    private fun showSortAndFilterDialog() {
        val context = requireContext()
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 48, 0, 48)
            setBackgroundResource(R.drawable.bg_dialog_rounded)
        }

        // 标题：筛选
        val titleFilter = TextView(context).apply {
            text = "筛选内容"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#1E88E5"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(64, 0, 64, 16)
        }
        dialogView.addView(titleFilter)

        // 开关：隐藏已拥有
        val switchFilter = MaterialSwitch(context).apply {
            text = "隐藏已在库中的游戏"
            textSize = 16f
            isChecked = isFilteringOwned
            setPadding(64, 0, 64, 0)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        dialogView.addView(switchFilter)

        // 分割线
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 2).apply {
                setMargins(0, 32, 0, 32)
            }
            setBackgroundColor(android.graphics.Color.parseColor("#10000000"))
        }
        dialogView.addView(divider)

        // 标题：排序
        val titleSort = TextView(context).apply {
            text = "排序方式"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#1E88E5"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(64, 0, 64, 16)
        }
        dialogView.addView(titleSort)

        // 排序选项
        val sortOptions = arrayOf("销量 (默认)", "现价 (低 → 高)", "现价 (高 → 低)", "折扣 (高 → 低)", "好评率 (高 → 低)")
        val radioGroup = RadioGroup(context).apply {
            setPadding(48, 0, 48, 0)
        }

        for (i in sortOptions.indices) {
            val rb = RadioButton(context).apply {
                text = sortOptions[i]
                id = i
                textSize = 15f
                setPadding(24, 16, 24, 16)
                layoutParams = RadioGroup.LayoutParams(-1, -2)
            }
            if (i == sortMode) rb.isChecked = true
            radioGroup.addView(rb)
        }
        dialogView.addView(radioGroup)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        // 确定按钮
        val confirmBtn = Button(context).apply {
            text = "确定"
            background = null
            setTextColor(android.graphics.Color.parseColor("#1E88E5"))
            setOnClickListener {
                isFilteringOwned = switchFilter.isChecked
                sortMode = radioGroup.checkedRadioButtonId
                if (sortMode == -1) sortMode = 0

                applySortAndFilter()
                dialog.dismiss()
            }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                gravity = android.view.Gravity.END
                setMargins(0, 16, 48, 0)
            }
        }
        dialogView.addView(confirmBtn)

        dialog.show()
    }

    private fun applySortAndFilter() {
        var list = if (isFilteringOwned) {
            rawList.filter { !MainActivity.ownedGameIds.contains(it.id) }
        } else {
            rawList
        }

        list = when (sortMode) {
            1 -> list.sortedBy { it.priceVal }
            2 -> list.sortedByDescending { it.priceVal }
            3 -> list.sortedByDescending { it.discount }
            4 -> list.sortedByDescending { it.reviewScore }
            else -> list
        }

        if (::adapter.isInitialized) {
            adapter.updateData(list)
            rv.scrollToPosition(0)
        } else {
            adapter = MarketAdapter(list)
            rv.adapter = adapter
        }
    }

    // 并发加载特惠游戏
    private fun loadSpecialsViaSearch() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                //配置更长的超时时间 (30秒)
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val totalPages = 2

                val deferredResults = (0 until totalPages).map { pageIndex ->
                    async {
                        // 在每个异步任务内部加 try-catch
                        // 这样即使某一页请求超时，也不会导致整个 App 闪退，只会少显示那一页的数据
                        try {
                            val start = pageIndex * 100
                            val url = "${PROXY_URL}search/results/?query&start=$start&count=100&dynamic_data=&sort_by=_ASC&specials=1&infinite=1&l=schinese&cc=cn&category1=998"

                            val request = Request.Builder().url(url).build()
                            val response = client.newCall(request).execute()
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
                val mergedList = allBatches.flatten()
                val distinctList = mergedList.distinctBy { it.name }

                withContext(Dispatchers.Main) {
                    if (distinctList.isEmpty()) {
                        Toast.makeText(context, "未获取到数据", Toast.LENGTH_SHORT).show()
                    }
                    rawList = distinctList
                    applySortAndFilter()
                    progress.visibility = View.GONE
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                }
            }
        }
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
            "Gold", "Ultimate", "Premium", "组合包", "纪念版"
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
                if (id == 0) continue

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

                list.add(MarketGame(id, name, standardImgUrl, priceStr, originStr, discount, null, rawImgUrl, priceVal, reviewScore))

            } catch (e: Exception) {}
        }
        return list
    }
}