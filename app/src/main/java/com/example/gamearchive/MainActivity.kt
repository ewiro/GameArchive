package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    companion object {
        val ownedGameIds = mutableSetOf<Int>()
        lateinit var apiServiceGlobal: SteamApiService
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val retrofit = Retrofit.Builder()
            .baseUrl(AppConfig.PROXY_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiServiceGlobal = retrofit.create(SteamApiService::class.java)
        LocaleHelper.currentApiLanguage = LocaleHelper.getApiLanguage(this)

        setContent {
            // 顶层：监听设置变更，每次变更触发 key 重组（含 MiuixTheme）
            var settingsVersion by remember { mutableIntStateOf(0) }
            val lifecycleOwner = LocalLifecycleOwner.current
            val context = LocalContext.current

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        // 每次返回主页时刷新状态栏图标颜色
                        (context as? android.app.Activity)?.let {
                            ThemeUtils.applyStatusBarAppearance(it)
                        }
                        if (ThemeUtils.isChanged) {
                            ThemeUtils.isChanged = false
                            LocaleHelper.currentApiLanguage = LocaleHelper.getApiLanguage(context)
                            settingsVersion++
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // 读取主题模式 — 放在 key 外才能让 settingsVersion 驱动重组
            val themeMode = ThemeUtils.getThemeMode(context)
            val colorSchemeMode = when (themeMode) {
                0 -> top.yukonga.miuix.kmp.theme.ColorSchemeMode.Light
                1 -> top.yukonga.miuix.kmp.theme.ColorSchemeMode.Dark
                else -> top.yukonga.miuix.kmp.theme.ColorSchemeMode.System
            }

            key(settingsVersion) {
                MiuixTheme(
                    controller = top.yukonga.miuix.kmp.theme.ThemeController(
                        colorSchemeMode = colorSchemeMode
                    )
                ) {
                    MainScreen()
                }
            }
        }
    }
}

// LiveData → Compose State
@Composable
private fun <T> LiveData<T>.observeAsState(): State<T?> {
    val state = remember { mutableStateOf(value) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(this, lifecycleOwner) {
        val observer = Observer<T> { state.value = it }
        observe(lifecycleOwner, observer)
        onDispose { removeObserver(observer) }
    }
    return state
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    var bottomBarVisible by remember { mutableStateOf(true) }
    var topBarVisible by remember { mutableStateOf(true) }
    val selectedTab = pagerState.currentPage

    val libraryListState = rememberLazyListState()
    val specialsListState = rememberLazyListState()
    val activeListState = if (selectedTab == 0) libraryListState else specialsListState

    // 滑动方向检测（仅同页滑动触发显隐，切页时只重置记数、不触发动画）
    val lastScrollIndex = remember { mutableIntStateOf(0) }
    val lastTab = remember { mutableIntStateOf(selectedTab) }
    LaunchedEffect(activeListState.firstVisibleItemIndex, selectedTab) {
        val idx = activeListState.firstVisibleItemIndex
        if (selectedTab != lastTab.intValue) {
            // 切页了：只重置追踪，不做显隐判断
            lastTab.intValue = selectedTab
        } else {
            if (idx > lastScrollIndex.intValue && idx > 1) {
                bottomBarVisible = false; topBarVisible = false
            } else if (idx < lastScrollIndex.intValue) {
                bottomBarVisible = true; topBarVisible = true
            }
        }
        lastScrollIndex.intValue = idx
    }

    val scope = rememberCoroutineScope()

    // 状态栏高度（顶栏叠加层需要）
    val statusBarHeight = remember {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }
    val statusBarDp = (statusBarHeight / context.resources.displayMetrics.density).dp
    val topBarHeightDp = 48.dp + statusBarDp + 4.dp

    // 排序弹窗状态（从 SpecialsScreen 提升上来）
    var showSortDialog by remember { mutableStateOf(false) }

    // 动画偏移量
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topBarOffsetY by animateDpAsState(
        targetValue = if (topBarVisible) 0.dp else -topBarHeightDp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
    )
    val bottomBarOffsetY by animateDpAsState(
        targetValue = if (bottomBarVisible) 0.dp else 60.dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
    )

    Surface(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize()) {

        // ── 内容层：HorizontalPager 填满全屏 ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true
        ) { page ->
            when (page) {
                0 -> LibraryScreen(
                    listState = libraryListState,
                    onNavigateToDetail = { appId, name, headerUrl, price ->
                        context.startActivity(Intent(context, DetailActivity::class.java).apply {
                            putExtra("APP_ID", appId); putExtra("APP_NAME", name)
                            putExtra("HEADER_URL", headerUrl); putExtra("APP_PRICE", price)
                        })
                    },
                    onNavigateToSettings = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }
                )
                1 -> SpecialsScreen(
                    listState = specialsListState,
                    showSortDialog = showSortDialog,
                    onDismissSortDialog = { showSortDialog = false },
                    onNavigateToDetail = { appId, name, headerUrl, price ->
                        context.startActivity(Intent(context, DetailActivity::class.java).apply {
                            putExtra("APP_ID", appId); putExtra("APP_NAME", name)
                            putExtra("HEADER_URL", headerUrl); putExtra("APP_PRICE", price)
                        })
                    }
                )
            }
        }

        // ── 顶栏叠加层 ──
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = with(density) { topBarOffsetY.toPx() }
                    alpha = 0.999f
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp)
                    .padding(top = statusBarDp + 4.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(if (selectedTab == 0) R.string.nav_library else R.string.nav_specials),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                if (selectedTab == 0) {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Image(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = context.getString(R.string.settings_title),
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                        )
                    }
                } else {
                    IconButton(onClick = { showSortDialog = true }) {
                        Image(
                            painter = painterResource(R.drawable.ic_sort),
                            contentDescription = "Sort",
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                        )
                    }
                }
            }
        }

        // ── 底栏叠加层 ──
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = with(density) { bottomBarOffsetY.toPx() }
                    alpha = 0.999f
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 库存 Tab
                Column(
                    modifier = Modifier
                        .clickable { scope.launch { pagerState.animateScrollToPage(0) } }
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(if (selectedTab == 0) R.drawable.ic_lib_filled else R.drawable.ic_lib_outlined),
                        contentDescription = context.getString(R.string.nav_library),
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(if (selectedTab == 0) Color(0xFF3482FF) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    )
                    Text(text = context.getString(R.string.nav_library), fontSize = 10.sp,
                        color = if (selectedTab == 0) Color(0xFF3482FF) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                // 特惠 Tab
                Column(
                    modifier = Modifier
                        .clickable { scope.launch { pagerState.animateScrollToPage(1) } }
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(if (selectedTab == 1) R.drawable.ic_sale_filled else R.drawable.ic_sale_outlined),
                        contentDescription = context.getString(R.string.nav_specials),
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(if (selectedTab == 1) Color(0xFF3482FF) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    )
                    Text(text = context.getString(R.string.nav_specials), fontSize = 10.sp,
                        color = if (selectedTab == 1) Color(0xFF3482FF) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }
    } // Box
    } // Surface
}

// ────────── 库存页 ──────────

@Composable
private fun LibraryScreen(
    listState: LazyListState,
    onNavigateToDetail: (Int, String, String, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val context = LocalContext.current
    val games by viewModel.games.observeAsState()
    val player by viewModel.player.observeAsState()
    val level by viewModel.level.observeAsState()
    val loading by viewModel.loading.observeAsState()
    val priceMap = viewModel.priceMap

    val apiKey = UserPrefs.getApiKey(context)
    val steamId = UserPrefs.getSteamId(context)

    LaunchedEffect(Unit) { viewModel.loadIfNeeded(apiKey, steamId) }

    LaunchedEffect(viewModel.error.value) {
        viewModel.error.value?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    val isGrouping = ThemeUtils.isGroupingEnabled(context)
    val sortMode = ThemeUtils.getSortMode(context)
    val showProfile = UserPrefs.isShowProfile(context)
    val gameList = games ?: emptyList()

    // 分组折叠状态
    var recentExpanded by remember { mutableStateOf(true) }
    var playedExpanded by remember { mutableStateOf(false) }
    var unplayedExpanded by remember { mutableStateOf(false) }

    // 开启状态分组后自动展开所有分组
    LaunchedEffect(isGrouping) {
        if (isGrouping) {
            recentExpanded = true
            playedExpanded = true
            unplayedExpanded = true
        }
    }

    val sortedGames = remember(gameList, sortMode) {
        if (sortMode == 0) gameList.sortedByDescending { it.playtime_forever }
        else gameList.sortedBy { it.name }
    }

    // 顶栏叠加层占位高度
    val statusBarHeight = remember {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }
    val topBarInsetDp = 48.dp + (statusBarHeight / context.resources.displayMetrics.density).dp + 4.dp

    if (loading == true && gameList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            InfiniteProgressIndicator()
        }
    } else {
        PullToRefresh(
            isRefreshing = loading == true,
            onRefresh = { viewModel.refresh(apiKey, steamId) },
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topBarInsetDp, bottom = 72.dp)
        ) {
            if (showProfile && player != null) {
                item("profile") {
                    ProfileHeader(player!!, gameList.size, gameList.sumOf { it.playtime_forever } / 60.0, level ?: 0)
                }
            }

            if (!isGrouping) {
                items(sortedGames, key = { "g_${it.appid}" }) { game ->
                    GameItem(game, priceMap[game.appid] ?: "", onClick = {
                        onNavigateToDetail(game.appid, game.name,
                            "https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appid}/header.jpg",
                            priceMap[game.appid] ?: "Free / Unknown")
                    })
                }
            } else {
                val recent = sortedGames.filter { (it.playtime_2weeks ?: 0) > 0 }
                val played = sortedGames.filter { it.playtime_forever > 0 && !recent.any { r -> r.appid == it.appid } }
                val unplayed = sortedGames.filter { it.playtime_forever == 0 }

                if (recent.isNotEmpty()) {
                    item("hdr_recent") {
                        GroupHeader(
                            title = context.getString(R.string.library_group_recent, recent.size),
                            expanded = recentExpanded,
                            onClick = { recentExpanded = !recentExpanded }
                        )
                    }
                    if (recentExpanded) {
                        items(recent, key = { "r_${it.appid}" }) { game ->
                            GameItem(game, priceMap[game.appid] ?: "") { onNavigateToDetail(game.appid, game.name,
                                "https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appid}/header.jpg",
                                priceMap[game.appid] ?: "Free / Unknown") }
                        }
                    }
                }
                if (played.isNotEmpty()) {
                    item("hdr_played") {
                        GroupHeader(
                            title = context.getString(R.string.library_group_played, played.size),
                            expanded = playedExpanded,
                            onClick = { playedExpanded = !playedExpanded }
                        )
                    }
                    if (playedExpanded) {
                        items(played, key = { "p_${it.appid}" }) { game ->
                            GameItem(game, priceMap[game.appid] ?: "") { onNavigateToDetail(game.appid, game.name,
                                "https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appid}/header.jpg",
                                priceMap[game.appid] ?: "Free / Unknown") }
                        }
                    }
                }
                if (unplayed.isNotEmpty()) {
                    item("hdr_unplayed") {
                        GroupHeader(
                            title = context.getString(R.string.library_group_unplayed, unplayed.size),
                            expanded = unplayedExpanded,
                            onClick = { unplayedExpanded = !unplayedExpanded }
                        )
                    }
                    if (unplayedExpanded) {
                        items(unplayed, key = { "u_${it.appid}" }) { game ->
                            GameItem(game, priceMap[game.appid] ?: "") { onNavigateToDetail(game.appid, game.name,
                                "https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appid}/header.jpg",
                                priceMap[game.appid] ?: "Free / Unknown") }
                        }
                    }
                }
            }
        }
        } // close PullToRefresh
    }
}

// ── 游玩时长徽章颜色映射 ──
private val BadgeColors = mapOf(
    0   to Color(0xFFCCCCD6), // 0h — 灰色
    200 to Color(0xFFD42517), // 200h+ — 红色
    100 to Color(0xFFFB8B05), // 100h+ — 橙色
    50  to Color(0xFF7E1671), // 50h+ — 紫色
    20  to Color(0xFF1772B4), // 20h+ — 蓝色
)
private val BadgeDefaultColor = Color(0xFF20894D) // 默认 — 绿色

// ── 状态颜色 ──
private val StatusOnlineColor = Color(0xFFB3E5FC)
private val StatusOfflineColor = Color(0xFFE0E0E0)
private val StatusInGameColor = Color(0xFFA3CF06)

@Composable
private fun ProfileHeader(player: PlayerInfo, gameCount: Int, totalHours: Double, level: Int) {
    val context = LocalContext.current
    val customBgUrl = UserPrefs.getCustomBgUrl(context)
    val customFrameUrl = UserPrefs.getCustomFrameUrl(context)
    val customAvatar = UserPrefs.getCustomAvatarUrl(context)
    val textShadow = androidx.compose.ui.graphics.Shadow(
        color = Color(0x80000000), offset = androidx.compose.ui.geometry.Offset(1f, 1f), blurRadius = 2f
    )

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .background(Color(0xFF3482FF), RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
    ) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
            // 自定义背景图 + 半透明遮罩
            if (customBgUrl.isNotEmpty()) {
                AsyncImage(
                    model = customBgUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.matchParentSize().background(Color(0x40000000)))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // 上半区：头像 + 名字 + 等级 + 状态
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 24.dp, end = 20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // 头像 + 挂件框 — 挂件为外框，头像居中在内
                    Box(modifier = Modifier.size(70.dp)) {
                        // 头像 68dp 居中，与挂件边框贴合
                        AsyncImage(
                            model = if (customAvatar.isNotEmpty()) customAvatar else player.avatarfull,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).align(Alignment.Center),
                            contentScale = ContentScale.Crop
                        )
                        // 挂件铺满外框，叠在头像上方
                        if (customFrameUrl.isNotEmpty()) {
                            AsyncImage(
                                model = customFrameUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    // 名字 + 等级 + 状态
                    Column(modifier = Modifier.padding(start = 24.dp).weight(1f)) {
                        Text(
                            text = player.personaname,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                        )
                        Text(
                            text = "Lv. $level",
                            color = Color(0xFFDDDDDD),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                        )
                        Spacer(Modifier.height(2.dp))
                        // 在线状态
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (statusText, statusColor) = if (player.gameextrainfo != null) {
                                context.getString(R.string.profile_playing) to StatusInGameColor
                            } else if (player.personastate == 0) {
                                context.getString(R.string.profile_offline) to StatusOfflineColor
                            } else {
                                context.getString(R.string.profile_online) to StatusOnlineColor
                            }
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                            )
                            // 正在玩的游戏名（跑马灯）
                            val gameName = player.gameextrainfo
                            if (gameName != null) {
                                Text(
                                    text = gameName,
                                    color = StatusInGameColor,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(start = 6.dp),
                                    style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                                )
                            }
                        }
                    }
                }

                // 下半区：统计数据
                Row(
                    modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 24.dp)
                ) {
                    // 总时长
                    Column {
                        Text(
                            text = if (totalHours < 0.05) "0h" else String.format("%.1fh", totalHours),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                        )
                        Text(
                            text = context.getString(R.string.profile_playtime_label),
                            color = Color(0xFFCCCCCC),
                            fontSize = 11.sp,
                            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                        )
                    }
                    Spacer(Modifier.width(32.dp))
                    // 库存数量
                    Column {
                        Text(
                            text = "$gameCount",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                        )
                        Text(
                            text = context.getString(R.string.profile_count_label),
                            color = Color(0xFFCCCCCC),
                            fontSize = 11.sp,
                            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                        )
                    }
                }
            }
        }
    }
}

// ── 分组标题（可折叠） ──
@Composable
private fun GroupHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 90f else 0f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = rotation },
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

// ── 封面 URL 构建（含 BF6 + 缓存回退） ──
private const val BF6_APPID = 2807960

private fun buildHeaderUrl(context: Context, appId: Int): Any {
    if (appId == BF6_APPID) return R.drawable.bf6_header
    val cache = context.getSharedPreferences("steam_header_cache", Context.MODE_PRIVATE)
    return cache.getString("header_$appId", null)
        ?: "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
}

// ── 游戏卡片（库存） ──
@Composable
private fun GameItem(game: GameInfo, price: String, onClick: () -> Unit) {
    val h = game.playtime_forever / 60.0
    val badgeColor = when {
        h >= 200 -> BadgeColors[200]!!
        h >= 100 -> BadgeColors[100]!!
        h >= 50  -> BadgeColors[50]!!
        h >= 20  -> BadgeColors[20]!!
        h >  0   -> BadgeDefaultColor
        else     -> BadgeColors[0]!!
    }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
        // 封面：130dp×61dp, 6dp 圆角（含 BF6 本地封面 + 404 回退）
        val headerUrl = buildHeaderUrl(context, game.appid)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(headerUrl)
                .crossfade(true)
                .memoryCacheKey(game.appid.toString())
                .listener(
                    onError = { _, _ ->
                        if (game.appid == BF6_APPID) return@listener
                        val cache = context.getSharedPreferences("steam_header_cache", Context.MODE_PRIVATE)
                        if (cache.contains("header_${game.appid}")) return@listener
                        // 异步查 Steam API 获取真实封面 URL
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                val resp = MainActivity.apiServiceGlobal.getGameDetails(
                                    game.appid, l = LocaleHelper.currentApiLanguage
                                )
                                val realUrl = resp[game.appid.toString()]?.data?.header_image
                                if (!realUrl.isNullOrEmpty()) {
                                    cache.edit().putString("header_${game.appid}", realUrl).apply()
                                }
                            } catch (_: Exception) {}
                        }
                    }
                )
                .build(),
            contentDescription = null,
            modifier = Modifier
                .width(130.dp).height(61.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 14.dp).height(61.dp)
        ) {
            // 第一行：游戏名 + 时长徽章（近期时长在徽章下方）
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = game.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Column(horizontalAlignment = Alignment.End) {
                    // 时长胶囊徽章
                    Row(
                        modifier = Modifier
                            .background(badgeColor, RoundedCornerShape(9.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_time),
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            colorFilter = ColorFilter.tint(Color.White)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (h < 0.05) "0h" else String.format("%.1fh", h),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    // 近期时长（徽章下方）
                    if (game.playtime_2weeks != null && game.playtime_2weeks > 0) {
                        Text(
                            text = "+${String.format("%.1f", game.playtime_2weeks / 60.0)}h",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3482FF),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // 好评率 — 置底对齐封面底部
            val reviewCache = context.getSharedPreferences("steam_reviews_cache", Context.MODE_PRIVATE)
            var reviewText by remember { mutableStateOf(
                reviewCache.getString("review_${game.appid}_${LocaleHelper.currentApiLanguage}", null)
            ) }
            LaunchedEffect(game.appid) {
                if (reviewText != null) return@LaunchedEffect
                try {
                    kotlinx.coroutines.delay(100L)
                    val resp = MainActivity.apiServiceGlobal.getGameReviews(
                        game.appid, l = LocaleHelper.currentApiLanguage
                    )
                    val summary = resp.query_summary
                    if (summary != null && summary.total_reviews > 0) {
                        val rate = (summary.total_positive.toDouble() / summary.total_reviews * 100).toInt()
                        val text = context.getString(R.string.review_score_format, rate)
                        reviewText = text
                        reviewCache.edit().putString("review_${game.appid}_${LocaleHelper.currentApiLanguage}", text).apply()
                    }
                } catch (_: Exception) {}
            }
            ReviewScore(reviewText)
        }
        } // close Row
        // 价格 — 在封面行下方
        if (price.isNotEmpty()) {
            Text(
                text = price,
                fontSize = 12.sp,
                color = Color(0xFFFF6600),
                modifier = Modifier.padding(start = 14.dp + 130.dp, top = 2.dp)
            )
        }
    } // close outer Column
}

// ── 好评率文字 ──
@Composable
private fun ReviewScore(text: String?) {
    if (text == null) return
    val score = try { text.filter { it.isDigit() }.toInt() } catch (_: Exception) { -1 }
    val color = when {
        score >= 95 -> Color(0xFFE65100)
        score >= 70 -> Color(0xFF1565C0)
        score >= 40 -> Color(0xFF616161)
        else        -> Color(0xFFD32F2F)
    }
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(top = 2.dp)
    )
}

// ────────── 特惠页 ──────────

@Composable
private fun SpecialsScreen(
    listState: LazyListState,
    showSortDialog: Boolean,
    onDismissSortDialog: () -> Unit,
    onNavigateToDetail: (Int, String, String, String) -> Unit,
    viewModel: SpecialsViewModel = viewModel()
) {
    val context = LocalContext.current
    val rawList by viewModel.rawList.observeAsState()
    val loading by viewModel.loading.observeAsState()

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    LaunchedEffect(viewModel.error.value) {
        viewModel.error.value?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    val gameList = rawList ?: emptyList()
    var filteredList by remember { mutableStateOf<List<MarketGame>>(emptyList()) }
    LaunchedEffect(gameList, viewModel.isFilteringOwned, viewModel.sortMode) {
        var list = gameList.toList()
        if (viewModel.isFilteringOwned) list = list.filter { !MainActivity.ownedGameIds.contains(it.id) }
        list = when (viewModel.sortMode) {
            1 -> list.sortedBy { it.priceVal }
            2 -> list.sortedByDescending { it.priceVal }
            3 -> list.sortedByDescending { it.discount }
            4 -> list.sortedByDescending { it.reviewScore }
            else -> list
        }
        filteredList = list
    }

    // 顶栏叠加层占位高度
    val statusBarHeight = remember {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }
    val topBarInsetDp = 48.dp + (statusBarHeight / context.resources.displayMetrics.density).dp + 4.dp

    if (loading == true && filteredList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            InfiniteProgressIndicator()
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topBarInsetDp, bottom = 72.dp)
        ) {
            itemsIndexed(filteredList, key = { idx, item -> "s_${item.id}_$idx" }) { _, game ->
                MarketGameItem(game) {
                    onNavigateToDetail(game.id, game.name, game.imgUrl, game.finalPriceStr)
                }
            }
        }
    }

    if (showSortDialog) {
        SortDialog(
            currentSort = viewModel.sortMode,
            isFilteringOwned = viewModel.isFilteringOwned,
            onSortSelected = { viewModel.sortMode = it; onDismissSortDialog() },
            onFilterToggle = { viewModel.isFilteringOwned = !viewModel.isFilteringOwned },
            onDismiss = onDismissSortDialog
        )
    }
}

@Composable
private fun SortDialog(
    currentSort: Int,
    isFilteringOwned: Boolean,
    onSortSelected: (Int) -> Unit,
    onFilterToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sortOptions = listOf(
        R.string.specials_sort_sales to "默认",
        R.string.specials_sort_price_asc to "价格从低到高",
        R.string.specials_sort_price_desc to "价格从高到低",
        R.string.specials_sort_discount to "折扣最大",
        R.string.specials_sort_rating to "好评最高"
    )

    Box(
        modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.padding(32.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = context.getString(R.string.specials_sort_title),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                sortOptions.forEachIndexed { i, (labelRes, _) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSortSelected(i) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentSort == i, onClick = { onSortSelected(i) })
                        Spacer(Modifier.width(8.dp))
                        Text(text = context.getString(labelRes))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onFilterToggle() }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        state = if (isFilteringOwned) androidx.compose.ui.state.ToggleableState.On
                                else androidx.compose.ui.state.ToggleableState.Off,
                        onClick = { onFilterToggle() }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = context.getString(R.string.specials_filter_hide_owned))
                }
            }
        }
    }
}

@Composable
private fun MarketGameItem(game: MarketGame, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 封面：130dp×61dp, 6dp 圆角（标准 URL 失败时回退备用 URL）
        var coverUrl by remember { mutableStateOf(game.imgUrl) }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(coverUrl)
                .crossfade(true)
                .listener(onError = { _, _ ->
                    val backup = game.backupImgUrl
                    if (!backup.isNullOrEmpty() && coverUrl != backup) {
                        coverUrl = backup
                    }
                })
                .build(),
            contentDescription = null,
            modifier = Modifier
                .width(130.dp).height(61.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        // 游戏名 + 好评率（中间区域，高度对齐封面 61dp）
        Column(
            modifier = Modifier.weight(1f).padding(start = 14.dp, end = 8.dp).height(61.dp)
        ) {
            Text(
                text = game.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.weight(1f))
            // 好评率 — 置底对齐封面底部
            if (game.reviewScore > 0) {
                val color = when {
                    game.reviewScore >= 95 -> Color(0xFFE65100)
                    game.reviewScore >= 70 -> Color(0xFF1565C0)
                    game.reviewScore >= 40 -> Color(0xFF616161)
                    else -> Color(0xFFD32F2F)
                }
                Text(
                    text = "${game.reviewScore}% " + context.getString(R.string.detail_positive).trim(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        // 价格区域（右侧对齐）
        Column(horizontalAlignment = Alignment.End) {
            if (game.discount > 0) {
                // 折扣胶囊 — 绿底黑字
                Box(
                    modifier = Modifier
                        .background(Color(0xFFA1CD44), RoundedCornerShape(7.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "-${game.discount}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF000400)
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            // 原价（划线）
            if (!game.originalPriceStr.isNullOrEmpty() && game.discount > 0) {
                Text(
                    text = game.originalPriceStr,
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    style = androidx.compose.ui.text.TextStyle(
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                    )
                )
            }
            // 现价
            Text(
                text = game.finalPriceStr,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
