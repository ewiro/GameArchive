package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.load
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    // 动态获取API Key和Steam ID
    private val apiKey: String
        get() = UserPrefs.getApiKey(requireContext())

    private val steamId: String
        get() = UserPrefs.getSteamId(requireContext())

    private lateinit var rv: RecyclerView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: View
    private lateinit var gifLoader: ImageLoader
    private lateinit var topBar: View
    private lateinit var tvTitle: TextView
    private lateinit var btnSettings: View

    // 游戏列表数据
    private var rawGameList: List<GameInfo> = emptyList()

    private var playerInfo: PlayerInfo? = null
    private var playerLevel: Int = 0
    private val priceMap = mutableMapOf<Int, String>()
    private val collapsedGroups = mutableSetOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.recyclerView)
        swipe = view.findViewById(R.id.swipeRefresh)
        progress = view.findViewById(R.id.progressBar)
        topBar = view.findViewById(R.id.topBarContainer)
        tvTitle = view.findViewById(R.id.tvTitle)
        btnSettings = view.findViewById(R.id.btnSettings)

        // 初始化GIF加载器
        gifLoader = ImageLoader.Builder(requireContext())
            .components { if (SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory()) }
            .build()

        rv.layoutManager = LinearLayoutManager(context)
        rv.itemAnimator = null

        // 列表滑动监听，控制顶栏和底栏显隐
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

        swipe.setColorSchemeColors(ThemeUtils.COLOR_PALETTE[5])
        swipe.setOnRefreshListener { loadData() }
        btnSettings.setOnClickListener { startActivity(Intent(context, SettingsActivity::class.java)) }

        loadData()
    }

    private fun animateTopBar(visible: Boolean) {
        val targetY = if (visible) 0f else -topBar.height.toFloat()
        if (topBar.translationY != targetY) {
            topBar.animate().translationY(targetY).setDuration(200).start()
        }
    }

    override fun onResume() {
        super.onResume()
        if (ThemeUtils.isChanged) {
            ThemeUtils.isChanged = false
            requireActivity().recreate()
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (apiKey.isEmpty() || steamId.isEmpty()) {
                    Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (!swipe.isRefreshing) progress.visibility = View.VISIBLE

                // 这三行代码会同时发出网络请求，不用排队
                val gameDeferred = async { MainActivity.apiServiceGlobal.getOwnedGames(apiKey, steamId) }
                val userDeferred = async { MainActivity.apiServiceGlobal.getPlayerSummaries(apiKey, steamId) }
                val levelDeferred = async {
                    try {
                        MainActivity.apiServiceGlobal.getSteamLevel(apiKey, steamId)
                    } catch (e: Exception) { null } // 等级失败不影响其他
                }

                // 获取库存游戏
                val gameRes = MainActivity.apiServiceGlobal.getOwnedGames(apiKey, steamId)
                rawGameList = gameRes.response.games

                // 黑名单过滤逻辑
                val allGames = gameRes.response.games
                val blackListIds = setOf(3081410)
                // 必须使用 filter 生成新的列表并赋值给 rawGameList
                rawGameList = allGames.filter { game ->
                    !blackListIds.contains(game.appid)
                }

                // 更新全局拥有的游戏ID列表
                MainActivity.ownedGameIds.clear()
                MainActivity.ownedGameIds.addAll(rawGameList.map { it.appid })

                // 获取玩家信息
                val userRes = MainActivity.apiServiceGlobal.getPlayerSummaries(apiKey, steamId)
                if (userRes.response.players.isNotEmpty()) playerInfo = userRes.response.players[0]

                // 获取Steam等级
                try {
                    val levelRes = MainActivity.apiServiceGlobal.getSteamLevel(apiKey, steamId)
                    playerLevel = levelRes.response.player_level ?: 0
                } catch (e: Exception) { playerLevel = 0 }

                // 批量获取前20个游戏的价格
                fetchBatchPrices(rawGameList.sortedByDescending { it.playtime_forever }.take(20))

                updateListUI()
                progress.visibility = View.GONE
                swipe.isRefreshing = false
            } catch (e: Exception) {
                progress.visibility = View.GONE
                swipe.isRefreshing = false
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    private suspend fun fetchBatchPrices(games: List<GameInfo>) {
        if (games.isEmpty()) return
        try {
            val ids = games.joinToString(",") { it.appid.toString() }
            val response = MainActivity.apiServiceGlobal.getGamePrices(ids)

            for ((idStr, details) in response) {
                val id = idStr.toInt()
                if (details.success && details.data?.price_overview != null) {
                    priceMap[id] = details.data.price_overview.final_formatted ?: "¥ --"
                } else {
                    priceMap[id] = "免费/未知"
                }
            }
        } catch (e: Exception) { }
    }

    private fun updateListUI() {
        if (rawGameList.isEmpty()) return
        val context = requireContext()
        val isGrouping = ThemeUtils.isGroupingEnabled(context)
        val isRecentGroup = ThemeUtils.isGroupRecentEnabled(context)
        val sortMode = ThemeUtils.getSortMode(context)

        fun sortGames(list: List<GameInfo>) = if (sortMode == 0) list.sortedByDescending { it.playtime_forever } else list.sortedBy { it.name }

        val adapters = mutableListOf<RecyclerView.Adapter<*>>()

        // 检查开关状态
        val showProfile = UserPrefs.isShowProfile(context)

        // 只有当 开关打开 且 数据不为空 时，才添加头部
        if (showProfile && playerInfo != null) {
            val totalHours = rawGameList.sumOf { it.playtime_forever } / 60
            adapters.add(ProfileHeaderAdapter(playerInfo!!, rawGameList.size, totalHours, playerLevel, gifLoader))
        }

        if (!isGrouping) {
            adapters.add(GameAdapter(sortGames(rawGameList), priceMap))
        } else {
            // 分组显示逻辑
            var remaining = rawGameList
            if (isRecentGroup) {
                val rawRecent = remaining.filter { (it.playtime_2weeks ?: 0) > 0 }
                val recent = rawRecent.sortedByDescending { it.playtime_2weeks }
                if (recent.isNotEmpty()) {
                    val title = "近期活跃 (${recent.size})"
                    adapters.add(createHeader(title))
                    if (!collapsedGroups.contains(title)) adapters.add(GameAdapter(recent, priceMap))
                    remaining = remaining.filter { !recent.map { r->r.appid }.contains(it.appid) }
                }
            }
            val played = sortGames(remaining.filter { it.playtime_forever > 0 })
            if (played.isNotEmpty()) {
                val title = "已玩游戏 (${played.size})"
                adapters.add(createHeader(title))
                if (!collapsedGroups.contains(title)) adapters.add(GameAdapter(played, priceMap))
            }
            val unplayed = sortGames(remaining.filter { it.playtime_forever == 0 })
            if (unplayed.isNotEmpty()) {
                val title = "堆积库存 (${unplayed.size})"
                adapters.add(createHeader(title))
                if (!collapsedGroups.contains(title)) adapters.add(GameAdapter(unplayed, priceMap))
            }
        }
        rv.adapter = ConcatAdapter(adapters)
    }

    private fun createHeader(title: String) = HeaderAdapter(title, collapsedGroups.contains(title)) {
        if (collapsedGroups.contains(title)) collapsedGroups.remove(title) else collapsedGroups.add(title)
        updateListUI()
    }

    // 分组标题适配器
    class HeaderAdapter(val title: String, val isCollapsed: Boolean, val onClick: () -> Unit) : RecyclerView.Adapter<HeaderAdapter.HeaderViewHolder>() {
        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvHeaderTitle)
            val ivArrow: ImageView = view.findViewById(R.id.ivArrow)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_header, parent, false))
        override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
            holder.tvTitle.text = title
            holder.ivArrow.rotation = if (isCollapsed) 0f else 90f
            holder.itemView.setOnClickListener { onClick() }
        }
        override fun getItemCount() = 1
    }

    // 游戏列表适配器
    class GameAdapter(val games: List<GameInfo>, val priceMap: Map<Int, String>) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {
        class GameViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivGameIcon: ImageView = view.findViewById(R.id.ivGameIcon)
            val tvGameName: TextView = view.findViewById(R.id.tvGameName)
            val tvPlayTime: TextView = view.findViewById(R.id.tvPlayTime)
            val tvPlayTime2Weeks: TextView = view.findViewById(R.id.tvPlayTime2Weeks)
            val cvTimeBadge: CardView = view.findViewById(R.id.cvTimeBadge)
            val ivTimeIcon: ImageView = view.findViewById(R.id.ivTimeIcon)
            val tvReviewRate: TextView = view.findViewById(R.id.tvReviewRate)
            val llTimeBadge: View = view.findViewById(R.id.llTimeBadge)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = GameViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_game, parent, false))
        override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
            val game = games[position]
            holder.tvGameName.text = game.name

            holder.itemView.setOnClickListener {
                val intent = Intent(holder.itemView.context, DetailActivity::class.java)
                intent.putExtra("APP_ID", game.appid); intent.putExtra("APP_NAME", game.name)
                intent.putExtra("HEADER_URL", "https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appid}/header.jpg")
                intent.putExtra("APP_PRICE", priceMap[game.appid] ?: "免费/未知")
                holder.itemView.context.startActivity(intent)
            }

            // 游戏时长
            val totalHours = (game.playtime_forever / 60.0).toInt()
            holder.tvPlayTime.text = "${totalHours}h"

            // 根据时长设置颜色 (中国传统色)
            val badgeColor = when {
                game.playtime_forever == 0 -> 0xFFCCCCD6.toInt() // 远山紫
                totalHours >= 200 -> 0xFFD42517.toInt()          // 鹤顶红
                totalHours >= 100 -> 0xFFFB8B05.toInt()          // 万寿菊黄
                totalHours >= 50 -> 0xFF7E1671.toInt()           // 魏紫
                totalHours >= 20 -> 0xFF1772B4.toInt()           // 群青
                else -> 0xFF20894D.toInt()                       // 宫殿绿
            }

            holder.llTimeBadge.setBackgroundColor(badgeColor)
            holder.tvPlayTime.setTextColor(Color.WHITE)
            holder.ivTimeIcon.setColorFilter(Color.WHITE)

            // 两周活跃时间
            if ((game.playtime_2weeks ?: 0) > 0) {
                holder.tvPlayTime2Weeks.text = "+${String.format("%.1f", game.playtime_2weeks!! / 60.0)}h"; holder.tvPlayTime2Weeks.visibility = View.VISIBLE
            } else holder.tvPlayTime2Weeks.visibility = View.GONE

            // 封面加载 (BF6特殊处理)
            val BF6_ID = 2807960
            if (game.appid == BF6_ID) {
                holder.ivGameIcon.load(R.drawable.bf6_header) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_grey)
                    error(R.drawable.placeholder_grey)
                    transformations(RoundedCornersTransformation(8f))
                }
            } else {
                holder.ivGameIcon.load("https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appid}/header.jpg") {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_grey)
                    error(R.drawable.placeholder_grey)
                    memoryCacheKey(game.appid.toString())
                }
            }

            // 好评率显示
            val context = holder.itemView.context
            val prefs = context.getSharedPreferences("steam_reviews_cache", Context.MODE_PRIVATE)
            val cachedReview = prefs.getString("review_${game.appid}", null)

            // 评价颜色逻辑
            fun getReviewColor(score: Int): Int {
                return when {
                    score >= 95 -> 0xFFE65100.toInt()
                    score >= 70 -> 0xFF1565C0.toInt()
                    score >= 40 -> 0xFF616161.toInt()
                    else -> 0xFFD32F2F.toInt()
                }
            }

            if (cachedReview != null) {
                holder.tvReviewRate.text = cachedReview
                holder.tvReviewRate.visibility = View.VISIBLE
                val score = try { cachedReview.filter { it.isDigit() }.toInt() } catch (e: Exception) { -1 }
                holder.tvReviewRate.setTextColor(getReviewColor(score))
            } else {
                holder.tvReviewRate.visibility = View.GONE
                // 联网获取评价
                (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
                    try {
                        delay(100 + (position % 5) * 50L)
                        val response = MainActivity.apiServiceGlobal.getGameReviews(game.appid)
                        val summary = response.query_summary
                        if (summary != null && summary.total_reviews > 0) {
                            val rate = (summary.total_positive.toDouble() / summary.total_reviews.toDouble() * 100).toInt()
                            val reviewText = "$rate% 好评"
                            holder.tvReviewRate.text = reviewText
                            holder.tvReviewRate.visibility = View.VISIBLE
                            holder.tvReviewRate.setTextColor(getReviewColor(rate))
                            prefs.edit().putString("review_${game.appid}", reviewText).apply()
                        }
                    } catch (e: Exception) { }
                }
            }
        }
        override fun getItemCount() = games.size
    }

    // 个人资料头部适配器
    class ProfileHeaderAdapter(val player: PlayerInfo, val count: Int, val hours: Int, val level: Int, val gifLoader: ImageLoader) : RecyclerView.Adapter<ProfileHeaderAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
            val ivAvatarFrame: ImageView = view.findViewById(R.id.ivAvatarFrame)
            val tvName: TextView = view.findViewById(R.id.tvPersonaName)
            val tvCount: TextView = view.findViewById(R.id.tvGameCount)
            val tvHours: TextView = view.findViewById(R.id.tvTotalHours)
            val ivBg: ImageView = view.findViewById(R.id.ivHeaderBg)
            val vMask: View = view.findViewById(R.id.vHeaderMask)
            val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            val tvGameStatus: TextView = view.findViewById(R.id.tvGameStatus)
            val tvLevel: TextView = view.findViewById(R.id.tvSteamLevel)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_profile_header, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.tvName.text = player.personaname; holder.tvCount.text = count.toString(); holder.tvHours.text = "$hours h"
            holder.tvLevel.text = "Lv. $level"

            // 获取自定义URL
            val customBgUrl = UserPrefs.getCustomBgUrl(holder.itemView.context)
            val customFrameUrl = UserPrefs.getCustomFrameUrl(holder.itemView.context)
            val customAvatar = UserPrefs.getCustomAvatarUrl(holder.itemView.context)

            // 1. 头像逻辑
            val avatarUrl = if (customAvatar.isNotEmpty()) customAvatar else player.avatarfull
            holder.ivAvatar.load(avatarUrl, gifLoader) {
                crossfade(false)
                placeholder(android.R.drawable.sym_def_app_icon)
            }

            // 2. 挂件逻辑
            if (customFrameUrl.isNotEmpty()) {
                holder.ivAvatarFrame.visibility = View.VISIBLE
                holder.ivAvatarFrame.load(customFrameUrl, gifLoader) { crossfade(true) }
            } else {
                holder.ivAvatarFrame.visibility = View.GONE
            }

            // 3. 背景逻辑
            if (customBgUrl.isNotEmpty()) {
                holder.ivBg.load(customBgUrl, gifLoader) {
                    crossfade(true)
                    error(android.R.color.transparent)
                }
                holder.vMask.visibility = View.VISIBLE
            } else {
                holder.ivBg.setImageDrawable(null)
                holder.vMask.visibility = View.GONE
            }

            // 4. 在线状态逻辑
            if (player.gameextrainfo != null) {
                holder.tvStatus.text = "正在游戏"
                holder.tvStatus.setTextColor(Color.parseColor("#A3CF06"))
                holder.tvGameStatus.text = player.gameextrainfo
                holder.tvGameStatus.visibility = View.VISIBLE
                holder.tvGameStatus.isSelected = true
            } else {
                holder.tvGameStatus.visibility = View.GONE
                holder.tvStatus.text = if (player.personastate == 0) "● 离线" else "● 在线"
                holder.tvStatus.setTextColor(if (player.personastate == 0) Color.parseColor("#E0E0E0") else Color.parseColor("#B3E5FC"))
            }
        }
        override fun getItemCount() = 1
    }
}