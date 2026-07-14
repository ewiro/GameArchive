package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.LiveData
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    companion object {
        val ownedGameIds = mutableSetOf<Int>()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeUtils.applyTheme(this)

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
                            // 语言变更 → 重建 Activity 让 attachBaseContext 重跑
                            if (ThemeUtils.hasLanguageChanged(context)) {
                                ThemeUtils.markLanguageApplied(context)
                                ThemeUtils.isChanged = false
                                (context as? android.app.Activity)?.recreate()
                                return@LifecycleEventObserver
                            }
                            ThemeUtils.isChanged = false
                            LocaleHelper.currentApiLanguage = LocaleHelper.getApiLanguage(context)
                            settingsVersion++
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            key(settingsVersion) {
                MiuixThemeForApp { MainScreen() }
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
    val specialsEnabled = ThemeUtils.isSpecialsEnabled(context)
    val pagerState = rememberPagerState(pageCount = { if (specialsEnabled) 2 else 1 })
    var bottomBarVisible by remember { mutableStateOf(true) }
    var topBarVisible by remember { mutableStateOf(true) }
    val selectedTab = pagerState.currentPage

    val libraryListState = rememberLazyListState()
    val specialsListState = rememberLazyListState()
    val activeListState = if (selectedTab == 0) libraryListState else specialsListState

    // 滑动检测：仅顶部显示栏，离开顶部即隐藏，回到顶部显示
    val lastTab = remember { mutableIntStateOf(selectedTab) }
    LaunchedEffect(activeListState.firstVisibleItemIndex, selectedTab) {
        val idx = activeListState.firstVisibleItemIndex
        if (selectedTab != lastTab.intValue) {
            lastTab.intValue = selectedTab
            bottomBarVisible = true; topBarVisible = true
        } else {
            bottomBarVisible = idx == 0
            topBarVisible = idx == 0
        }
    }

    val scope = rememberCoroutineScope()

    // 状态栏高度（顶栏叠加层需要）
    val statusBarDp = statusBarHeightDp()
    val topBarHeightDp = 48.dp + statusBarDp + 4.dp

    // 排序弹窗状态（从 SpecialsScreen 提升上来）
    var showSortDialog by remember { mutableStateOf(false) }

    // 动画偏移量
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topBarOffsetY by animateDpAsState(
        targetValue = if (topBarVisible) 0.dp else -topBarHeightDp,
        animationSpec = tween(durationMillis = DesignTokens.AnimDuration, easing = FastOutSlowInEasing)
    )
    val bottomBarOffsetY by animateDpAsState(
        targetValue = if (bottomBarVisible) 0.dp else 60.dp,
        animationSpec = tween(durationMillis = DesignTokens.AnimDuration, easing = FastOutSlowInEasing)
    )

    Surface(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize()) {

        // ── 内容层：HorizontalPager 填满全屏 ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true
        ) { page ->
            val navigateToDetail: (Int, String, String) -> Unit = { appId, name, price ->
                context.startActivity(Intent(context, DetailActivity::class.java).apply {
                    putExtra("APP_ID", appId); putExtra("APP_NAME", name); putExtra("APP_PRICE", price)
                })
            }

            when (page) {
                0 -> LibraryScreen(
                    listState = libraryListState,
                    onNavigateToDetail = navigateToDetail,
                    onNavigateToSettings = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }
                )
                1 -> SpecialsScreen(
                    listState = specialsListState,
                    showSortDialog = showSortDialog,
                    onDismissSortDialog = { showSortDialog = false },
                    onNavigateToDetail = navigateToDetail
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
                    text = context.getString(if (specialsEnabled && selectedTab == 1) R.string.nav_specials else R.string.nav_library),
                    fontWeight = FontWeight.Bold,
                    fontSize = DesignTokens.TextTitle.sp,
                    modifier = Modifier.weight(1f)
                )
                if (!specialsEnabled || selectedTab == 0) {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Image(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = context.getString(R.string.settings_title),
                            modifier = Modifier.size(DesignTokens.IconXl),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                        )
                    }
                } else {
                    IconButton(onClick = { showSortDialog = true }) {
                        Image(
                            painter = painterResource(R.drawable.ic_sort),
                            contentDescription = "Sort",
                            modifier = Modifier.size(DesignTokens.IconXl),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                        )
                    }
                }
            }
        }

        // ── 底栏叠加层（仅特惠开启时显示） ──
        if (specialsEnabled) {
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
                    .height(DesignTokens.BottomBarHeight)
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
                        modifier = Modifier.size(DesignTokens.IconXl),
                        colorFilter = ColorFilter.tint(if (selectedTab == 0) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
                    )
                    Text(text = context.getString(R.string.nav_library), fontSize = DesignTokens.TextCaption.sp,
                        color = if (selectedTab == 0) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
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
                        modifier = Modifier.size(DesignTokens.IconXl),
                        colorFilter = ColorFilter.tint(if (selectedTab == 1) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
                    )
                    Text(text = context.getString(R.string.nav_specials), fontSize = DesignTokens.TextCaption.sp,
                        color = if (selectedTab == 1) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
                }
            }
        }
        } // if specialsEnabled
    } // Box
    } // Surface
}

// ────────── 库存页 ──────────

@Composable
private fun LibraryScreen(
    listState: LazyListState,
    onNavigateToDetail: (Int, String, String) -> Unit,
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
        viewModel.error.value?.let { (resId, arg) ->
            val msg = if (arg != null) context.getString(resId, arg) else context.getString(resId)
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    val isGrouping = ThemeUtils.isGroupingEnabled(context)
    val sortMode = ThemeUtils.getSortMode(context)
    val showProfile = UserPrefs.isShowProfile(context)
    val gameList = games ?: emptyList()

    // 从详情页/设置页返回时刷新标记和标签显示
    val lifecycleOwner = LocalLifecycleOwner.current
    var listRefreshTrigger by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) listRefreshTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    // 标记筛选状态（-1 = 全部）
    var markFilter by remember { mutableIntStateOf(-1) }
    var searchQuery by remember { mutableStateOf("") }

    // 应用搜索 + 标记筛选
    val filteredGames = remember(sortedGames, markFilter, searchQuery, listRefreshTrigger) {
        var list = sortedGames
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        if (markFilter != -1) list = list.filter { GameMarks.getMark(context, it.appid) == markFilter }
        list
    }

    // 顶栏叠加层占位高度
    val topBarInsetDp = 48.dp + statusBarHeightDp() + 4.dp

    if (loading == true && gameList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            InfiniteProgressIndicator()
        }
    } else {
        PullToRefresh(
            isRefreshing = loading == true,
            onRefresh = { viewModel.refresh(apiKey, steamId); listRefreshTrigger++ },
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topBarInsetDp),
            refreshTexts = listOf(
                context.getString(R.string.general_pull_down),
                context.getString(R.string.general_release),
                context.getString(R.string.general_refreshing),
                context.getString(R.string.general_refreshed)
            ),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            item("top_spacer") { Spacer(Modifier.height(topBarInsetDp)) }
            if (showProfile && player != null) {
                item("profile") {
                    ProfileHeader(player!!, gameList.size, gameList.sumOf { it.playtime_forever } / 60.0, level ?: 0)
                }
            }

            // 搜索栏
            item("search_bar") {
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                var textFieldValue by remember(searchQuery) { mutableStateOf(searchQuery) }
                TextField(
                    value = textFieldValue,
                    onValueChange = { v: String ->
                        textFieldValue = v
                        searchQuery = v.trim()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    label = context.getString(R.string.library_search),
                    useLabelAsPlaceholder = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clickable {
                                        textFieldValue = ""
                                        searchQuery = ""
                                        focusManager.clearFocus()
                                    }
                                    .size(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✕",
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityHint)
                                )
                            }
                        }
                    },
                    singleLine = true
                )
            }

            // 标记筛选横条
            item("mark_filter") {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "全部" 按钮
                        MarkFilterChip(
                            label = context.getString(R.string.mark_filter_all),
                            selected = markFilter == -1,
                            color = null,
                            onClick = { markFilter = -1 }
                        )
                        GameMarks.markResIds.forEach { resId ->
                            MarkFilterChip(
                                label = context.getString(resId),
                                selected = markFilter == resId,
                                color = androidx.compose.ui.graphics.Color(
                                    GameMarks.statusColorMap[resId]!!
                                ),
                                onClick = {
                                    markFilter = if (markFilter == resId) -1 else resId
                                }
                            )
                        }
                    }
                    if (markFilter != -1) {
                        Text(
                            text = context.getString(R.string.library_filter_count, filteredGames.size),
                            fontSize = DesignTokens.TextBody2.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            if (!isGrouping || markFilter != -1) {
                items(filteredGames, key = { "g_${it.appid}" }) { game ->
                    GameItem(game, priceMap[game.appid] ?: "", refreshVersion = listRefreshTrigger, onClick = {
                        onNavigateToDetail(game.appid, game.name,
                            priceMap[game.appid] ?: "Free / Unknown")
                    })
                }
            } else {
                val recent = filteredGames.filter { (it.playtime_2weeks ?: 0) > 0 }
                val played = filteredGames.filter { it.playtime_forever > 0 && !recent.any { r -> r.appid == it.appid } }
                val unplayed = filteredGames.filter { it.playtime_forever == 0 }

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
                            GameItem(game, priceMap[game.appid] ?: "", refreshVersion = listRefreshTrigger) { onNavigateToDetail(game.appid, game.name, priceMap[game.appid] ?: "Free / Unknown") }
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
                            GameItem(game, priceMap[game.appid] ?: "", refreshVersion = listRefreshTrigger) { onNavigateToDetail(game.appid, game.name, priceMap[game.appid] ?: "Free / Unknown") }
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
                            GameItem(game, priceMap[game.appid] ?: "", refreshVersion = listRefreshTrigger) { onNavigateToDetail(game.appid, game.name, priceMap[game.appid] ?: "Free / Unknown") }
                        }
                    }
                }
            }
        }
        } // close PullToRefresh
    }
}

// ── 游玩时长徽章颜色 → DesignTokens.badgeColor() / badgeColorMap
// ── 状态颜色 → DesignTokens.Status*

@Composable
private fun ProfileHeader(player: PlayerInfo, gameCount: Int, totalHours: Double, level: Int) {
    val context = LocalContext.current
    val customBgUrl = UserPrefs.getCustomBgUrl(context)
    val customFrameUrl = UserPrefs.getCustomFrameUrl(context)
    val customAvatar = UserPrefs.getCustomAvatarUrl(context)
    val textShadow = androidx.compose.ui.graphics.Shadow(
        color = DesignTokens.TextShadowColor, offset = androidx.compose.ui.geometry.Offset(1f, 1f), blurRadius = 2f
    )

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .background(DesignTokens.AccentBlue, RoundedCornerShape(DesignTokens.CornerXLarge))
            .clip(RoundedCornerShape(DesignTokens.CornerXLarge))
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
                Box(modifier = Modifier.matchParentSize().background(DesignTokens.ProfileOverlay))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // 上半区：头像 + 名字 + 等级 + 状态
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 24.dp, end = 20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // 头像 + 挂件框 — 挂件为外框，头像居中在内
                    Box(modifier = Modifier.size(DesignTokens.AvatarOuter)) {
                        AsyncImage(
                            model = if (customAvatar.isNotEmpty()) customAvatar else player.avatarfull,
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.AvatarInner)
                                .align(Alignment.Center),
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
                            fontSize = DesignTokens.TextHeadline.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                        )
                        Text(
                            text = context.getString(R.string.profile_level, level),
                            color = DesignTokens.ProfileTextDim2,
                            fontSize = DesignTokens.TextBody2.sp,
                            fontWeight = FontWeight.Bold,
                            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                        )
                        Spacer(Modifier.height(2.dp))
                        // 在线状态
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (statusText, statusColor) = if (player.gameextrainfo != null) {
                                context.getString(R.string.profile_playing) to DesignTokens.StatusInGame
                            } else if (player.personastate == 0) {
                                context.getString(R.string.profile_offline) to DesignTokens.StatusOffline
                            } else {
                                context.getString(R.string.profile_online) to DesignTokens.StatusOnline
                            }
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = DesignTokens.TextBody2.sp,
                                fontWeight = FontWeight.Bold,
                                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                            )
                            // 正在玩的游戏名（跑马灯）
                            val gameName = player.gameextrainfo
                            if (gameName != null) {
                                Text(
                                    text = gameName,
                                    color = DesignTokens.StatusInGame,
                                    fontSize = DesignTokens.TextBody2.sp,
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
                            fontSize = DesignTokens.TextSubtitle.sp,
                            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                        )
                        Text(
                            text = context.getString(R.string.profile_playtime_label),
                            color = DesignTokens.ProfileTextDim1,
                            fontSize = DesignTokens.TextCaption.sp,
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
                            fontSize = DesignTokens.TextSubtitle.sp,
                            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                        )
                        Text(
                            text = context.getString(R.string.profile_count_label),
                            color = DesignTokens.ProfileTextDim1,
                            fontSize = DesignTokens.TextCaption.sp,
                            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                        )
                    }
                }
            }
        }
    }
}

// ── 分组标题（可折叠，chevron 箭头） ──
@Composable
private fun GroupHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = DesignTokens.AnimDuration, easing = FastOutSlowInEasing),
        label = "arrowRotation"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "<",
            fontSize = DesignTokens.TextSubtitle.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityEmphasis),
            modifier = Modifier.graphicsLayer { rotationZ = rotation }
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = DesignTokens.TextSubtitle.sp
        )
    }
}

// ── 标记筛选胶囊 ──
@Composable
private fun MarkFilterChip(label: String, selected: Boolean, color: Color?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.CornerLarge))
            .background(
                if (selected && color != null) color.copy(alpha = DesignTokens.OpacityChipBg)
                else MiuixTheme.colorScheme.surface.copy(alpha = DesignTokens.OpacityInactive)
            )
            .then(
                if (selected && color != null) Modifier.border(DesignTokens.BorderThick, color, RoundedCornerShape(DesignTokens.CornerLarge))
                else Modifier.border(DesignTokens.BorderThin, MiuixTheme.colorScheme.outline.copy(alpha = DesignTokens.OpacityDisabled), RoundedCornerShape(DesignTokens.CornerLarge))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = DesignTokens.TextBody2.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected && color != null) color else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityEmphasis)
        )
    }
}

// ── 封面 URL 构建（含 BF6 + 缓存回退 + 90天过期） ──
private const val BF6_APPID = 2807960
private const val HEADER_CACHE_TTL_MS = 90L * 24 * 3600 * 1000  // 90 天

private fun buildHeaderUrl(context: Context, appId: Int): Any {
    if (appId == BF6_APPID) return R.drawable.bf6_header
    val cache = context.getSharedPreferences("steam_header_cache", Context.MODE_PRIVATE)
    val cached = readCacheWithExpiry(cache, "header_$appId", HEADER_CACHE_TTL_MS)
    return cached ?: "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
}

// 通用缓存读取（含过期检查）。格式 "value|timestamp"，过期返回 null
private fun readCacheWithExpiry(prefs: android.content.SharedPreferences, key: String, ttlMs: Long): String? {
    val entry = prefs.getString(key, null) ?: return null
    val parts = entry.split("|", limit = 2)
    if (parts.size < 2) {
        prefs.edit().remove(key).apply()
        return null
    }
    val ts = parts[1].toLongOrNull() ?: 0L
    if (System.currentTimeMillis() - ts > ttlMs) {
        prefs.edit().remove(key).apply()
        return null
    }
    return parts[0]
}

// ── 游戏卡片（库存） ──
@Composable
private fun GameItem(game: GameInfo, price: String, refreshVersion: Int = 0, onClick: () -> Unit) {
    val h = game.playtime_forever / 60.0
    val badgeColor = DesignTokens.badgeColor(h)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val resp = GameArchiveApp.apiService.getGameDetails(
                                    game.appid, l = LocaleHelper.currentApiLanguage
                                )
                                val realUrl = resp[game.appid.toString()]?.data?.header_image
                                if (!realUrl.isNullOrEmpty()) {
                                    cache.edit().putString(
                                        "header_${game.appid}",
                                        "$realUrl|${System.currentTimeMillis()}"
                                    ).apply()
                                }
                            } catch (_: Exception) {}
                        }
                    }
                )
                .build(),
            contentDescription = null,
            modifier = Modifier
                .width(DesignTokens.CoverWidth).height(DesignTokens.CoverHeight)
                .clip(RoundedCornerShape(DesignTokens.CornerMedium)),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp).heightIn(min = DesignTokens.CoverHeight)
        ) {
            // 第一行：游戏名 + 时长徽章（近期时长在徽章下方）
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = game.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = DesignTokens.TextBody1.sp,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Column(horizontalAlignment = Alignment.End) {
                    // 时长胶囊徽章
                    Row(
                        modifier = Modifier
                            .background(badgeColor, RoundedCornerShape(DesignTokens.CornerMedium))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_time),
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.IconSm),
                            colorFilter = ColorFilter.tint(Color.White)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (h < 0.05) "0h" else String.format("%.1fh", h),
                            fontSize = DesignTokens.TextCaption.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    // 近期时长（徽章下方）
                    if (game.playtime_2weeks != null && game.playtime_2weeks > 0) {
                        Text(
                            text = "+${String.format("%.1f", game.playtime_2weeks / 60.0)}h",
                            fontSize = DesignTokens.TextCaption.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // 游玩标记 + 好评率 — 底部同行，左标右评
            val REVIEW_CACHE_TTL_MS = 30L * 24 * 3600 * 1000
            val reviewCache = context.getSharedPreferences("steam_reviews_cache", Context.MODE_PRIVATE)
            var reviewText by remember { mutableStateOf(
                readCacheWithExpiry(reviewCache, "review_${game.appid}_${LocaleHelper.currentApiLanguage}", REVIEW_CACHE_TTL_MS)
            ) }
            LaunchedEffect(game.appid) {
                if (reviewText != null) return@LaunchedEffect
                try {
                    kotlinx.coroutines.delay(100L)
                    val resp = GameArchiveApp.apiService.getGameReviews(
                        game.appid, l = LocaleHelper.currentApiLanguage
                    )
                    val summary = resp.query_summary
                    if (summary != null && summary.total_reviews > 0) {
                        val rate = (summary.total_positive.toDouble() / summary.total_reviews * 100).toInt()
                        val text = context.getString(R.string.review_score_format, rate)
                        reviewText = text
                        reviewCache.edit().putString(
                            "review_${game.appid}_${LocaleHelper.currentApiLanguage}",
                            "$text|${System.currentTimeMillis()}"
                        ).apply()
                    }
                } catch (_: Exception) {}
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReviewScore(reviewText)
                Spacer(Modifier.weight(1f))
                val markRes = GameMarks.getMark(context, game.appid)
                if (markRes in GameMarks.markResIds) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(GameMarks.statusColorMap[markRes]!!), CircleShape)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = context.getString(markRes),
                            fontSize = DesignTokens.TextBody2.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                        )
                    }
                }
            }
        }
        } // close Row
        // 价格 — 在封面行下方
        if (price.isNotEmpty()) {
            Text(
                text = price,
                fontSize = DesignTokens.TextBody2.sp,
                color = DesignTokens.PriceOrange,
                modifier = Modifier.padding(start = 14.dp + 130.dp, top = 2.dp)
            )
        }
        // 卡片间分割线 — 微调：改 height 调粗细，改 alpha 调深浅，改 top 调上间距
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(DesignTokens.DividerHeight)
                .background(MiuixTheme.colorScheme.outline.copy(alpha = DesignTokens.OpacityDisabled))
        )
    } // close outer Column
}

// ── 好评率文字（弱化灰色） ──
@Composable
private fun ReviewScore(text: String?) {
    if (text == null) return
    Text(
        text = text,
        fontSize = DesignTokens.TextCaption.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
        modifier = Modifier.padding(top = 2.dp)
    )
}

// ────────── 特惠页 ──────────

@Composable
private fun SpecialsScreen(
    listState: LazyListState,
    showSortDialog: Boolean,
    onDismissSortDialog: () -> Unit,
    onNavigateToDetail: (Int, String, String) -> Unit,
    viewModel: SpecialsViewModel = viewModel()
) {
    val context = LocalContext.current
    val rawList by viewModel.rawList.observeAsState()
    val loading by viewModel.loading.observeAsState()

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    LaunchedEffect(viewModel.error.value) {
        viewModel.error.value?.let { (resId, arg) ->
            val msg = if (arg != null) context.getString(resId, arg) else context.getString(resId)
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    val gameList = rawList ?: emptyList()
    var filteredList by remember { mutableStateOf<List<MarketGame>>(emptyList()) }
    LaunchedEffect(gameList, viewModel.isFilteringOwned, viewModel.sortMode, viewModel.priceFilter) {
        var list = gameList.toList()
        if (viewModel.isFilteringOwned) list = list.filter { !MainActivity.ownedGameIds.contains(it.id) }
        // 价格筛选
        if (viewModel.priceFilter > 0) {
            list = when (viewModel.priceFilter) {
                1 -> list.filter { it.priceVal <= 10.0 }
                2 -> list.filter { it.priceVal in 10.01..50.0 }
                3 -> list.filter { it.priceVal in 50.01..100.0 }
                4 -> list.filter { it.priceVal > 100.0 }
                else -> list
            }
        }
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
    val topBarInsetDp = 48.dp + statusBarHeightDp() + 4.dp

    Box(modifier = Modifier.fillMaxSize()) {
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
                    onNavigateToDetail(game.id, game.name, game.finalPriceStr)
                }
            }
        }
    }

    if (showSortDialog) {
        Dialog(
            onDismissRequest = onDismissSortDialog,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            SortDialog(
                currentSort = viewModel.sortMode,
                currentPrice = viewModel.priceFilter,
                isFilteringOwned = viewModel.isFilteringOwned,
                onSortSelected = { viewModel.sortMode = it; onDismissSortDialog() },
                onPriceSelected = { viewModel.priceFilter = it },
                onFilterToggle = { viewModel.isFilteringOwned = !viewModel.isFilteringOwned },
                onDismiss = onDismissSortDialog
            )
        }
    }
    }
}

@Composable
private fun SortDialog(
    currentSort: Int,
    currentPrice: Int,
    isFilteringOwned: Boolean,
    onSortSelected: (Int) -> Unit,
    onPriceSelected: (Int) -> Unit,
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
    val priceOptions = listOf(
        context.getString(R.string.specials_price_all),
        "¥0 - ¥10",
        "¥10 - ¥50",
        "¥50 - ¥100",
        "¥100+"
    )
    var showSortOptions by remember { mutableStateOf(false) }
    var showPriceOptions by remember { mutableStateOf(false) }

    // 阻止穿透点击关闭弹窗
    val stopPropagation = Modifier.clickable(enabled = false, onClick = {})

    Box(
        modifier = Modifier.fillMaxSize().background(DesignTokens.ScrimDark).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.padding(32.dp).then(stopPropagation)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = context.getString(R.string.specials_filter),
                    fontWeight = FontWeight.Bold,
                    fontSize = DesignTokens.TextBody1.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                // ── 排序方式 ──
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                        .clickable { showSortOptions = !showSortOptions; showPriceOptions = false }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.specials_sort_title),
                        fontSize = DesignTokens.TextSubtitle.sp,
                        fontWeight = systemFontWeight(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(32.dp))
                    Text(
                        text = context.getString(sortOptions[currentSort]),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                    )
                    Spacer(Modifier.width(4.dp))
                    DropdownArrowEndAction(
                        actionColor = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                    )
                }
                AnimatedVisibility(
                    visible = showSortOptions,
                    enter = expandVertically(animationSpec = tween(DesignTokens.AnimDuration)) + fadeIn(animationSpec = tween(DesignTokens.AnimDuration)),
                    exit = shrinkVertically(animationSpec = tween(DesignTokens.AnimDuration)) + fadeOut(animationSpec = tween(DesignTokens.AnimDuration))
                ) {
                    Column {
                        sortOptions.forEachIndexed { i, resId ->
                            val sel = currentSort == i
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onSortSelected(i); showSortOptions = false }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = context.getString(resId),
                                    fontSize = DesignTokens.TextSubtitle.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                    softWrap = false,
                                    modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(32.dp))
                                Text(text = "✓",
                                    fontSize = DesignTokens.TextSubtitle.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (sel) MiuixTheme.colorScheme.primary else Color.Transparent)
                            }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(DesignTokens.DividerHeight).padding(vertical = 8.dp).background(MiuixTheme.colorScheme.outline))
                // ── 价格区间 ──
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                        .clickable { showPriceOptions = !showPriceOptions; showSortOptions = false }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.specials_price_filter),
                        fontSize = DesignTokens.TextSubtitle.sp,
                        fontWeight = systemFontWeight(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(32.dp))
                    Text(
                        text = priceOptions[currentPrice],
                        fontSize = DesignTokens.TextBody1.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                    )
                    Spacer(Modifier.width(4.dp))
                    DropdownArrowEndAction(
                        actionColor = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                    )
                }
                AnimatedVisibility(
                    visible = showPriceOptions,
                    enter = expandVertically(animationSpec = tween(DesignTokens.AnimDuration)) + fadeIn(animationSpec = tween(DesignTokens.AnimDuration)),
                    exit = shrinkVertically(animationSpec = tween(DesignTokens.AnimDuration)) + fadeOut(animationSpec = tween(DesignTokens.AnimDuration))
                ) {
                    Column {
                        priceOptions.forEachIndexed { i, label ->
                            val sel = currentPrice == i
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onPriceSelected(i); showPriceOptions = false }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = label,
                                    fontSize = DesignTokens.TextSubtitle.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                    softWrap = false,
                                    modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(32.dp))
                                Text(text = "✓",
                                    fontSize = DesignTokens.TextSubtitle.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (sel) MiuixTheme.colorScheme.primary else Color.Transparent)
                            }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(DesignTokens.DividerHeight).padding(vertical = 8.dp).background(MiuixTheme.colorScheme.outline))
                // ── 隐藏已拥有 ──
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onFilterToggle() }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.specials_filter_hide_owned),
                        fontSize = DesignTokens.TextSubtitle.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = isFilteringOwned, onCheckedChange = { onFilterToggle() })
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
                .width(DesignTokens.CoverWidth).height(DesignTokens.CoverHeight)
                .clip(RoundedCornerShape(DesignTokens.CornerMedium)),
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
                fontSize = DesignTokens.TextBody1.sp
            )
            Spacer(Modifier.weight(1f))
            // 好评率 — 置底对齐封面底部
            if (game.reviewScore > 0) {
                val color = reviewColor(game.reviewScore)
                Text(
                    text = "${game.reviewScore}% " + context.getString(R.string.detail_positive).trim(),
                    fontSize = DesignTokens.TextCaption.sp,
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
                        .background(DesignTokens.DiscountGreen, RoundedCornerShape(DesignTokens.CornerMedium))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "-${game.discount}%",
                        fontSize = DesignTokens.TextCaption.sp,
                        fontWeight = FontWeight.Bold,
                        color = DesignTokens.DiscountGreenText
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            // 原价（划线）
            if (!game.originalPriceStr.isNullOrEmpty() && game.discount > 0) {
                Text(
                    text = game.originalPriceStr,
                    fontSize = DesignTokens.TextCaption.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive),
                    style = androidx.compose.ui.text.TextStyle(
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                    )
                )
            }
            // 现价
            Text(
                text = game.finalPriceStr,
                fontSize = DesignTokens.TextBody1.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
