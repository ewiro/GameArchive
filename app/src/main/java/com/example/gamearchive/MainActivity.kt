package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
            MiuixTheme {
                MainScreen()
            }
        }
    }
}

// LiveData → Compose State 辅助函数
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
    var selectedTab by remember { mutableStateOf(0) }
    val libraryListState = rememberLazyListState()
    val specialsListState = rememberLazyListState()
    var bottomBarVisible by remember { mutableStateOf(true) }

    // 滑动方向检测
    val activeListState = if (selectedTab == 0) libraryListState else specialsListState
    val lastIndex = remember { mutableStateOf(0) }
    val lastOffset = remember { mutableStateOf(0) }
    LaunchedEffect(activeListState.firstVisibleItemIndex, activeListState.firstVisibleItemScrollOffset) {
        val idx = activeListState.firstVisibleItemIndex
        val off = activeListState.firstVisibleItemScrollOffset
        if (idx > lastIndex.value) {
            bottomBarVisible = false // 向下滚动
        } else if (idx < lastIndex.value) {
            bottomBarVisible = true // 向上滚动
        }
        lastIndex.value = idx
        lastOffset.value = off
    }

    // 主题变更监听
    LaunchedEffect(Unit) {
        if (ThemeUtils.isChanged) {
            ThemeUtils.isChanged = false
            LocaleHelper.currentApiLanguage = LocaleHelper.getApiLanguage(context)
            (context as? android.app.Activity)?.recreate()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedTab) {
            0 -> LibraryScreen(
                listState = libraryListState,
                onNavigateToDetail = { appId, name, headerUrl, price ->
                    context.startActivity(Intent(context, DetailActivity::class.java).apply {
                        putExtra("APP_ID", appId)
                        putExtra("APP_NAME", name)
                        putExtra("HEADER_URL", headerUrl)
                        putExtra("APP_PRICE", price)
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
                        putExtra("APP_ID", appId)
                        putExtra("APP_NAME", name)
                        putExtra("HEADER_URL", headerUrl)
                        putExtra("APP_PRICE", price)
                    })
                }
            )
        }

        // 底部导航栏
        androidx.compose.animation.AnimatedVisibility(
            visible = bottomBarVisible,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    modifier = Modifier
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 8.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = context.getString(R.string.nav_library),
                        color = if (selectedTab == 0) Color.Unspecified else Color.Gray
                    )
                }
                Column(
                    modifier = Modifier
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 8.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = context.getString(R.string.nav_specials),
                        color = if (selectedTab == 1) Color.Unspecified else Color.Gray
                    )
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = context.getString(R.string.nav_library), modifier = Modifier.weight(1f))
            IconButton(onClick = onNavigateToSettings) {
                Text(text = context.getString(R.string.settings_title))
            }
        }

        if (loading == true && gameList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                InfiniteProgressIndicator()
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                                modifier = Modifier.padding(16.dp, 8.dp))
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
                                modifier = Modifier.padding(16.dp, 8.dp))
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
                                modifier = Modifier.padding(16.dp, 8.dp))
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
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(player.avatarfull).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(text = player.personaname)
                    Text(text = "Lv. $level")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(text = "$gameCount games · ${if (totalHours < 0.05) "0h" else String.format("%.1fh", totalHours)}")
        }
    }
}

@Composable
private fun GameItem(game: GameInfo, price: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://cdn.cloudflare.steamstatic.com/steam/apps/${game.appid}/header.jpg")
                    .crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.width(120.dp).height(56.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = game.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val h = game.playtime_forever / 60.0
                Text(text = if (h < 0.05) "0h" else String.format("%.1fh", h))
                if (price.isNotEmpty()) Text(text = price)
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = context.getString(R.string.nav_specials), modifier = Modifier.weight(1f))
            IconButton(onClick = { showSortDialog = true }) {
                Text(text = "Sort")
            }
        }

        if (loading == true && filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                InfiniteProgressIndicator()
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
        R.string.specials_sort_sales,
        R.string.specials_sort_price_asc,
        R.string.specials_sort_price_desc,
        R.string.specials_sort_discount,
        R.string.specials_sort_rating
    )

    Box(
        modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.padding(32.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = context.getString(R.string.specials_sort_title))
                Spacer(Modifier.height(12.dp))
                sortOptions.forEachIndexed { i, labelRes ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSortSelected(i) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentSort == i, onClick = { onSortSelected(i) })
                        Spacer(Modifier.width(8.dp))
                        Text(text = context.getString(labelRes))
                    }
                }
                Spacer(Modifier.height(12.dp))
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(game.imgUrl).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.width(120.dp).height(56.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = game.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = game.finalPriceStr)
                    if (game.discount > 0) {
                        Text(text = "  -${game.discount}%", color = Color(0xFFFF4444))
                    }
                }
            }
        }
    }
}
