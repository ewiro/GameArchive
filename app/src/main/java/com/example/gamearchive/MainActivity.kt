package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
                    if (event == Lifecycle.Event.ON_RESUME && ThemeUtils.isChanged) {
                        ThemeUtils.isChanged = false
                        LocaleHelper.currentApiLanguage = LocaleHelper.getApiLanguage(context)
                        settingsVersion++
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

    // 监听页面变化同步选中状态
    val selectedTab = pagerState.currentPage

    // 用于从子页面接收滑动状态的共享列表状态
    val libraryListState = rememberLazyListState()
    val specialsListState = rememberLazyListState()
    val activeListState = if (selectedTab == 0) libraryListState else specialsListState

    // 滑动方向检测：控制底栏显隐
    val lastIndex = remember { mutableIntStateOf(0) }
    LaunchedEffect(activeListState.firstVisibleItemIndex) {
        val idx = activeListState.firstVisibleItemIndex
        if (idx > lastIndex.intValue && idx > 1) bottomBarVisible = false
        else if (idx < lastIndex.intValue) bottomBarVisible = true
        lastIndex.intValue = idx
    }

    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // 内容区（HorizontalPager 支持滑动切换）
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
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
                    onNavigateToDetail = { appId, name, headerUrl, price ->
                        context.startActivity(Intent(context, DetailActivity::class.java).apply {
                            putExtra("APP_ID", appId); putExtra("APP_NAME", name)
                            putExtra("HEADER_URL", headerUrl); putExtra("APP_PRICE", price)
                        })
                    }
                )
            }
        }

        // 底部导航栏
        androidx.compose.animation.AnimatedVisibility(
            visible = bottomBarVisible,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
        ) {
            Surface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(bottom = 8.dp),
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
                            painter = painterResource(
                                if (selectedTab == 0) R.drawable.ic_lib_filled else R.drawable.ic_lib_outlined
                            ),
                            contentDescription = context.getString(R.string.nav_library),
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(
                                if (selectedTab == 0) Color(0xFF3482FF) else Color(0xFF999999)
                            )
                        )
                        Text(
                            text = context.getString(R.string.nav_library),
                            fontSize = 10.sp,
                            color = if (selectedTab == 0) Color(0xFF3482FF) else Color(0xFF999999)
                        )
                    }
                    // 特惠 Tab
                    Column(
                        modifier = Modifier
                            .clickable { scope.launch { pagerState.animateScrollToPage(1) } }
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(
                                if (selectedTab == 1) R.drawable.ic_sale_filled else R.drawable.ic_sale_outlined
                            ),
                            contentDescription = context.getString(R.string.nav_specials),
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(
                                if (selectedTab == 1) Color(0xFF3482FF) else Color(0xFF999999)
                            )
                        )
                        Text(
                            text = context.getString(R.string.nav_specials),
                            fontSize = 10.sp,
                            color = if (selectedTab == 1) Color(0xFF3482FF) else Color(0xFF999999)
                        )
                    }
                }
            }
        }
    }
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

    val sortedGames = remember(gameList, sortMode) {
        if (sortMode == 0) gameList.sortedByDescending { it.playtime_forever }
        else gameList.sortedBy { it.name }
    }

    // 为状态栏留空间
    val statusBarHeight = remember {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏 — 带状态栏间距
        Surface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp)
                    .padding(top = (statusBarHeight / context.resources.displayMetrics.density).dp + 8.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.nav_library),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNavigateToSettings) {
                    Image(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = context.getString(R.string.settings_title),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (loading == true && gameList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                InfiniteProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
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
                            Text(text = context.getString(R.string.library_group_recent, recent.size),
                                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                                fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        items(recent, key = { "r_${it.appid}" }) { game ->
                            GameItem(game, priceMap[game.appid] ?: "") { onNavigateToDetail(game.appid, game.name,
                                "https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appid}/header.jpg",
                                priceMap[game.appid] ?: "Free / Unknown") }
                        }
                    }
                    if (played.isNotEmpty()) {
                        item("hdr_played") {
                            Text(text = context.getString(R.string.library_group_played, played.size),
                                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                                fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        items(played, key = { "p_${it.appid}" }) { game ->
                            GameItem(game, priceMap[game.appid] ?: "") { onNavigateToDetail(game.appid, game.name,
                                "https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appid}/header.jpg",
                                priceMap[game.appid] ?: "Free / Unknown") }
                        }
                    }
                    if (unplayed.isNotEmpty()) {
                        item("hdr_unplayed") {
                            Text(text = context.getString(R.string.library_group_unplayed, unplayed.size),
                                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                                fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        items(unplayed, key = { "u_${it.appid}" }) { game ->
                            GameItem(game, priceMap[game.appid] ?: "") { onNavigateToDetail(game.appid, game.name,
                                "https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appid}/header.jpg",
                                priceMap[game.appid] ?: "Free / Unknown") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(player: PlayerInfo, gameCount: Int, totalHours: Double, level: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = player.avatarfull,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = player.personaname, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = "Lv. $level", fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$gameCount games  ·  ${if (totalHours < 0.05) "0h" else String.format("%.1fh", totalHours)}",
            fontSize = 13.sp
        )
    }
}

@Composable
private fun GameItem(game: GameInfo, price: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appid}/header.jpg")
                .crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.width(120.dp).height(56.dp),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp)
        ) {
            Text(
                text = game.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            val h = game.playtime_forever / 60.0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (h < 0.05) "0h" else String.format("%.1fh", h),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                if (game.playtime_2weeks != null && game.playtime_2weeks > 0) {
                    Text(
                        text = "  +${String.format("%.1f", game.playtime_2weeks / 60.0)}h recent",
                        fontSize = 11.sp,
                        color = Color(0xFF3482FF)
                    )
                }
            }
            if (price.isNotEmpty()) {
                Text(text = price, fontSize = 12.sp, color = Color(0xFFFF6600))
            }
        }
    }
}

// ────────── 特惠页 ──────────

@Composable
private fun SpecialsScreen(
    listState: LazyListState,
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

    var showSortDialog by remember { mutableStateOf(false) }

    val statusBarHeight = remember {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp)
                    .padding(top = (statusBarHeight / context.resources.displayMetrics.density).dp + 8.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.nav_specials),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showSortDialog = true }) {
                    Image(
                        painter = painterResource(R.drawable.ic_sort),
                        contentDescription = "Sort",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (loading == true && filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                InfiniteProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredList, key = { "s_${it.id}" }) { game ->
                    MarketGameItem(game) {
                        onNavigateToDetail(game.id, game.name, game.imgUrl, game.finalPriceStr)
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        SortDialog(
            currentSort = viewModel.sortMode,
            isFilteringOwned = viewModel.isFilteringOwned,
            onSortSelected = { viewModel.sortMode = it; showSortDialog = false },
            onFilterToggle = { viewModel.isFilteringOwned = !viewModel.isFilteringOwned },
            onDismiss = { showSortDialog = false }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(game.imgUrl).crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.width(120.dp).height(56.dp),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp)
        ) {
            Text(
                text = game.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = game.finalPriceStr, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (game.discount > 0) {
                    Text(
                        text = "  -${game.discount}%",
                        color = Color(0xFFFF4444),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!game.originalPriceStr.isNullOrEmpty()) {
                        Text(
                            text = "  ${game.originalPriceStr}",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
