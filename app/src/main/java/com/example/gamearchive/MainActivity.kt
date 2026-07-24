package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.icon.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@Suppress("DEPRECATION")
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
@Suppress("DEPRECATION")
private fun MainScreen() {
    val context = LocalContext.current
    val specialsEnabled = ThemeUtils.isSpecialsEnabled(context)
    val bangumiEnabled = ThemeUtils.isBangumiEnabled(context)
    val specialsPage = if (specialsEnabled) 1 else -1
    val bangumiPage = if (bangumiEnabled) (if (specialsEnabled) 2 else 1) else -1
    val activityPage = 1 + (if (specialsEnabled) 1 else 0) + (if (bangumiEnabled) 1 else 0)
    val pagerState = rememberPagerState(pageCount = { activityPage + 1 })
    var bottomBarVisible by remember { mutableStateOf(true) }
    var topBarVisible by remember { mutableStateOf(true) }
    val selectedTab = pagerState.currentPage

    val libraryViewModel: LibraryViewModel = viewModel()
    val bangumiViewModel: BangumiViewModel = viewModel()
    val libraryListState = rememberLazyListState()
    val specialsListState = rememberLazyListState()
    val bangumiListState = rememberLazyListState()
    val activityListState = rememberLazyListState()
    var bangumiTypeFilter by remember { mutableIntStateOf(2) }  // 默认在看
    var bangumiSearchQuery by remember { mutableStateOf("") }
    val activeListState = when (selectedTab) {
        0 -> libraryListState
        specialsPage -> specialsListState
        bangumiPage -> bangumiListState
        activityPage -> activityListState
        else -> libraryListState
    }

    val bgmUsername = UserPrefs.getBangumiUsername(context)
    val bgmAccessToken = UserPrefs.getBangumiAccessToken(context)
    LaunchedEffect(bangumiEnabled, bgmUsername, bgmAccessToken) {
        if (bangumiEnabled && bgmUsername.isNotBlank()) {
            bangumiViewModel.loadIfNeeded(bgmUsername, bgmAccessToken, context)
        }
    }

    // 滑动检测：仅顶部显示栏，离开顶部即隐藏，回到顶部显示
    LaunchedEffect(activeListState, selectedTab) {
        bottomBarVisible = true
        topBarVisible = true
        snapshotFlow { activeListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                bottomBarVisible = index == 0
                topBarVisible = index == 0
            }
    }

    val scope = rememberCoroutineScope()
    val navigateToPage: (Int) -> Unit = { targetPage ->
        if (targetPage != pagerState.currentPage) {
            scope.launch {
                if (abs(targetPage - pagerState.currentPage) == 1) {
                    pagerState.animateScrollToPage(
                        page = targetPage,
                        animationSpec = tween(
                            durationMillis = DesignTokens.AnimDuration,
                            easing = FastOutSlowInEasing
                        )
                    )
                } else {
                    pagerState.scrollToPage(targetPage)
                }
            }
        }
    }

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
                (context as? android.app.Activity)?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            val navigateToBangumiDetail: (Int, String, String, String) -> Unit = { id, name, nameCn, imageUrl ->
                context.startActivity(Intent(context, BangumiDetailActivity::class.java).apply {
                    putExtra("SUBJECT_ID", id)
                    putExtra("SUBJECT_NAME", name)
                    putExtra("SUBJECT_NAME_CN", nameCn)
                    putExtra("SUBJECT_IMAGE", imageUrl)
                })
            }

            when (page) {
                0 -> LibraryScreen(
                    listState = libraryListState,
                    onNavigateToDetail = navigateToDetail,
                    onNavigateToSettings = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                    viewModel = libraryViewModel
                )
                activityPage -> ActivityPage(
                    listState = activityListState,
                    libraryViewModel = libraryViewModel,
                    bangumiViewModel = bangumiViewModel,
                    onNavigateToDetail = navigateToDetail,
                    onNavigateToBangumiDetail = navigateToBangumiDetail
                )
                else -> {
                    if (specialsEnabled && page == 1) {
                        SpecialsScreen(
                            listState = specialsListState,
                            showSortDialog = showSortDialog,
                            onDismissSortDialog = { showSortDialog = false },
                            onNavigateToDetail = navigateToDetail
                        )
                    } else {
                        BangumiPage(
                            listState = bangumiListState,
                            typeFilter = bangumiTypeFilter,
                            onTypeFilterChange = { bangumiTypeFilter = it },
                            searchQuery = bangumiSearchQuery,
                            onSearchQueryChange = { bangumiSearchQuery = it },
                            onNavigateToDetail = navigateToBangumiDetail,
                            viewModel = bangumiViewModel
                        )
                    }
                }
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
                    text = context.getString(
                        when {
                            selectedTab == specialsPage -> R.string.nav_specials
                            selectedTab == bangumiPage -> R.string.nav_bangumi
                            selectedTab == activityPage -> R.string.nav_activity
                            else -> R.string.nav_library
                        }
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = DesignTokens.TextTitle.sp,
                    modifier = Modifier.weight(1f)
                )
                if (selectedTab == 0 || selectedTab == bangumiPage || selectedTab == activityPage) {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Image(
                            imageVector = MiuixIcons.Demibold.Settings,
                            contentDescription = context.getString(R.string.settings_title),
                            modifier = Modifier.size(DesignTokens.IconXl),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                        )
                    }
                } else {
                    IconButton(onClick = { showSortDialog = true }) {
                        Image(
                            imageVector = MiuixIcons.Demibold.Filter,
                            contentDescription = context.getString(R.string.general_sort),
                            modifier = Modifier.size(DesignTokens.IconXl),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                        )
                    }
                }
            }
        }

        // ── 底栏叠加层（至少2页时显示） ──
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {})
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
                val tabAnim0 by animateFloatAsState(
                    targetValue = if (selectedTab == 0) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                    label = "tab_anim_0"
                )
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = 0.85f + 0.15f * tabAnim0
                            scaleY = 0.85f + 0.15f * tabAnim0
                        }
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            navigateToPage(0)
                        }
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        imageVector = if (selectedTab == 0) MiuixIcons.Demibold.Home else MiuixIcons.Light.Home,
                        contentDescription = context.getString(R.string.nav_library),
                        modifier = Modifier.size(DesignTokens.IconXl),
                        colorFilter = ColorFilter.tint(if (selectedTab == 0) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
                    )
                    Text(text = context.getString(R.string.nav_library), fontSize = DesignTokens.TextCaption.sp,
                        color = if (selectedTab == 0) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
                }
                // 特惠 Tab（仅开启时显示）
                if (specialsEnabled) {
                    val tabAnim1 by animateFloatAsState(
                        targetValue = if (selectedTab == 1) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                        label = "tab_anim_1"
                    )
                    Column(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = 0.85f + 0.15f * tabAnim1
                                scaleY = 0.85f + 0.15f * tabAnim1
                            }
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                navigateToPage(1)
                            }
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            imageVector = if (selectedTab == 1) MiuixIcons.Demibold.Promotions else MiuixIcons.Light.Promotions,
                            contentDescription = context.getString(R.string.nav_specials),
                            modifier = Modifier.size(DesignTokens.IconXl),
                            colorFilter = ColorFilter.tint(if (selectedTab == 1) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
                        )
                        Text(text = context.getString(R.string.nav_specials), fontSize = DesignTokens.TextCaption.sp,
                            color = if (selectedTab == 1) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
                    }
                }
                // 动漫 Tab
                if (bangumiEnabled) {
                val tabAnimBgm by animateFloatAsState(
                    targetValue = if (selectedTab == bangumiPage) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                    label = "tab_anim_bgm"
                )
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = 0.85f + 0.15f * tabAnimBgm
                            scaleY = 0.85f + 0.15f * tabAnimBgm
                        }
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            navigateToPage(bangumiPage)
                        }
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        imageVector = if (selectedTab == bangumiPage) MiuixIcons.Demibold.Album else MiuixIcons.Light.Album,
                        contentDescription = context.getString(R.string.nav_bangumi),
                        modifier = Modifier.size(DesignTokens.IconXl),
                        colorFilter = ColorFilter.tint(if (selectedTab == bangumiPage) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
                    )
                    Text(text = context.getString(R.string.nav_bangumi), fontSize = DesignTokens.TextCaption.sp,
                        color = if (selectedTab == bangumiPage) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
                }
                }
                val tabAnimActivity by animateFloatAsState(
                    targetValue = if (selectedTab == activityPage) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                    label = "tab_anim_activity"
                )
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = 0.85f + 0.15f * tabAnimActivity
                            scaleY = 0.85f + 0.15f * tabAnimActivity
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            navigateToPage(activityPage)
                        }
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        imageVector = if (selectedTab == activityPage) {
                            MiuixIcons.Demibold.Recent
                        } else {
                            MiuixIcons.Light.Recent
                        },
                        contentDescription = context.getString(R.string.nav_activity),
                        modifier = Modifier.size(DesignTokens.IconXl),
                        colorFilter = ColorFilter.tint(
                            if (selectedTab == activityPage) {
                                DesignTokens.AccentBlue
                            } else {
                                MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive)
                            }
                        )
                    )
                    Text(
                        text = context.getString(R.string.nav_activity),
                        fontSize = DesignTokens.TextCaption.sp,
                        color = if (selectedTab == activityPage) {
                            DesignTokens.AccentBlue
                        } else {
                            MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive)
                        }
                    )
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

    LaunchedEffect(Unit) { viewModel.loadIfNeeded(apiKey, steamId, context) }

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
    val markSnapshot = remember(listRefreshTrigger) { GameMarks.getAllMarks(context) }
    val nameSnapshot = remember(listRefreshTrigger) { GameNames.getAllNames(context) }

    // 应用搜索 + 标记筛选
    val filteredGames = remember(sortedGames, markFilter, searchQuery, markSnapshot) {
        var list = sortedGames
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        if (markFilter != -1) {
            list = list.filter { markSnapshot[it.appid] == markFilter }
        } else {
            list = list.filter { markSnapshot[it.appid] != R.string.mark_abandoned }
        }
        list
    }

    // 顶栏叠加层占位高度
    val topBarInsetDp = 48.dp + statusBarHeightDp() + 4.dp

    Crossfade(loading == true && gameList.isEmpty(), animationSpec = tween(250)) { showSkeleton ->
    if (showSkeleton) {
        LibraryLoadingSkeleton(topBarInsetDp, showProfile)
    } else {
        PullToRefresh(
            isRefreshing = loading == true,
            onRefresh = { viewModel.refresh(apiKey, steamId, context); listRefreshTrigger++ },
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
                    GameItem(
                        game = game,
                        price = priceMap[game.appid] ?: "",
                        customName = nameSnapshot[game.appid],
                        markRes = markSnapshot[game.appid] ?: -1,
                        viewModel = viewModel,
                        onClick = {
                        onNavigateToDetail(game.appid, game.name,
                            priceMap[game.appid] ?: "Free / Unknown")
                    })
                }
            } else {
                val recent = filteredGames.filter { (it.playtime_2weeks ?: 0) > 0 }
                val recentIds = recent.mapTo(hashSetOf()) { it.appid }
                val played = filteredGames.filter {
                    it.playtime_forever > 0 && it.appid !in recentIds
                }
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
                            GameItem(game, priceMap[game.appid] ?: "", nameSnapshot[game.appid], markSnapshot[game.appid] ?: -1, viewModel) { onNavigateToDetail(game.appid, game.name, priceMap[game.appid] ?: "Free / Unknown") }
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
                            GameItem(game, priceMap[game.appid] ?: "", nameSnapshot[game.appid], markSnapshot[game.appid] ?: -1, viewModel) { onNavigateToDetail(game.appid, game.name, priceMap[game.appid] ?: "Free / Unknown") }
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
                            GameItem(game, priceMap[game.appid] ?: "", nameSnapshot[game.appid], markSnapshot[game.appid] ?: -1, viewModel) { onNavigateToDetail(game.appid, game.name, priceMap[game.appid] ?: "Free / Unknown") }
                        }
                    }
                }
            }
        }
        } // close PullToRefresh
    } // close if/else
    } // close Crossfade
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
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
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
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
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

// ── 特惠页封面白名单（standardImgUrl 失效的游戏，改用 backupImgUrl）──
private const val SPECIALS_WHITELIST_PREF = "specials_cover_whitelist"

private fun isSpecialsCoverWhitelisted(context: Context, appId: Int): Boolean {
    val prefs = context.getSharedPreferences(SPECIALS_WHITELIST_PREF, Context.MODE_PRIVATE)
    return prefs.getBoolean("w_$appId", false)
}

private fun addToSpecialsWhitelist(context: Context, appId: Int) {
    val prefs = context.getSharedPreferences(SPECIALS_WHITELIST_PREF, Context.MODE_PRIVATE)
    prefs.edit().putBoolean("w_$appId", true).apply()
}

// ── 游戏卡片（库存） ──
@Composable
private fun GameItem(
    game: GameInfo,
    price: String,
    customName: String?,
    markRes: Int,
    viewModel: LibraryViewModel,
    onClick: () -> Unit
) {
    val h = game.playtime_forever / 60.0
    val badgeColor = DesignTokens.badgeColor(h)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
        // 封面：130dp×61dp, 6dp 圆角（含 BF6 本地封面 + 404 回退）
        val headerUrl = buildHeaderUrl(context, game.appid)
        AsyncImage(
            model = remember(headerUrl, game.appid) {
                ImageRequest.Builder(context)
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
                    .build()
            },
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
                    text = customName ?: game.name,
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
            val reviewText by viewModel.reviewScore(context, game.appid).observeAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReviewScore(reviewText)
                Spacer(Modifier.weight(1f))
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
    val filteredList by remember(gameList) {
        derivedStateOf {
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
        list
        }
    }

    // 顶栏叠加层占位高度
    val topBarInsetDp = 48.dp + statusBarHeightDp() + 4.dp

    Box(modifier = Modifier.fillMaxSize()) {
    Crossfade(loading == true && filteredList.isEmpty(), animationSpec = tween(250)) { showSkeleton ->
    if (showSkeleton) {
        SpecialsSkeleton(topBarInsetDp)
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
    } // close Crossfade

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
                                Image(
                                imageVector = MiuixIcons.Basic.Check,
                                contentDescription = null,
                                modifier = Modifier.size(DesignTokens.IconMd),
                                colorFilter = ColorFilter.tint(if (sel) MiuixTheme.colorScheme.primary else Color.Transparent)
                            )
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
                                Image(
                                imageVector = MiuixIcons.Basic.Check,
                                contentDescription = null,
                                modifier = Modifier.size(DesignTokens.IconMd),
                                colorFilter = ColorFilter.tint(if (sel) MiuixTheme.colorScheme.primary else Color.Transparent)
                            )
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
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 封面：130dp×61dp, 6dp 圆角（白名单游戏直接用备用 URL；标准 URL 失败时回退并记入白名单）
        val whitelisted = remember(game.id) { isSpecialsCoverWhitelisted(context, game.id) }
        var coverUrl by remember(game.id, whitelisted) {
            mutableStateOf(if (whitelisted && !game.backupImgUrl.isNullOrEmpty()) game.backupImgUrl else game.imgUrl)
        }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(coverUrl)
                .crossfade(true)
                .listener(onError = { _, _ ->
                    val backup = game.backupImgUrl
                    if (!backup.isNullOrEmpty() && coverUrl != backup) {
                        addToSpecialsWhitelist(context, game.id)
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
                text = GameNames.getName(context, game.id) ?: game.name,
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

// ── 骨架屏 ──
@Composable
private fun shimmerBrush(): Brush {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { target = 1f; delay(800); target = 0f; delay(800) } }
    val progress by animateFloatAsState(target, tween(1600))
    val s = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    return Brush.linearGradient(listOf(Color.Transparent, s, Color.Transparent), Offset(progress * 400f - 200f, 0f), Offset(progress * 400f + 100f, 0f))
}


// ── 动漫条目骨架（100×140 封面 + 文字行 + 状态行） ──
@Composable
private fun BangumiSkeletonCard() {
    val bg = MiuixTheme.colorScheme.surfaceVariant
    val shimmer = shimmerBrush()
    val coverW = 80.dp; val coverH = 112.dp
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(coverW, coverH).clip(RoundedCornerShape(6.dp)).background(bg))
        Spacer(Modifier.width(12.dp))
        // 中：名字 + 话数
        Column(Modifier.weight(1f).height(coverH).padding(vertical = 2.dp)) {
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(56.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
        }
        Spacer(Modifier.width(8.dp))
        // 右：评分 + 评级 + 日期
        Column(Modifier.height(coverH).padding(vertical = 2.dp), horizontalAlignment = Alignment.End) {
            Box(Modifier.width(32.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(24.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(48.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
        }
    }
}

@Composable
private fun SkeletonCard() {
    val bg = MiuixTheme.colorScheme.surfaceVariant
    val shimmer = shimmerBrush()
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(DesignTokens.CoverWidth).height(DesignTokens.CoverHeight).clip(RoundedCornerShape(DesignTokens.CornerMedium)).background(bg))
        Column(Modifier.padding(start = 12.dp).height(DesignTokens.CoverHeight)) {
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth(0.35f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
        }
    }
}

@Composable
private fun ShimmerBox(
    w: Dp,
    h: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 4.dp
) {
    Box(modifier.width(w).height(h).clip(RoundedCornerShape(corner)).background(shimmerBrush()))
}

// 库存页顶部骨架
@Composable
private fun LibraryTopSkeleton(showProfile: Boolean) {
    val bg = MiuixTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxWidth()) {
        if (showProfile) {
            Box(Modifier
                .fillMaxWidth().height(196.dp).padding(horizontal = 18.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(DesignTokens.CornerXLarge)).background(bg)
            ) {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(DesignTokens.AvatarOuter).clip(CircleShape).background(shimmerBrush()))
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) { ShimmerBox(120.dp, 16.dp); Spacer(Modifier.height(8.dp)); ShimmerBox(60.dp, 14.dp, corner = 10.dp) }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(3) { Column(horizontalAlignment = Alignment.CenterHorizontally) { ShimmerBox(40.dp, 18.dp); Spacer(Modifier.height(4.dp)); ShimmerBox(28.dp, 10.dp) } }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 18.dp, vertical = 6.dp).clip(RoundedCornerShape(DesignTokens.CornerMedium)).background(bg))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(8) { ShimmerBox(60.dp, 32.dp, corner = 20.dp) } }
        repeat(2) { ShimmerBox(100.dp, 16.dp, modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)); repeat(2) { SkeletonCard() } }
    }
}

@Composable
private fun LibraryLoadingSkeleton(topInsetDp: androidx.compose.ui.unit.Dp, showProfile: Boolean) {
    val h = with(androidx.compose.ui.platform.LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
    val ch = DesignTokens.CoverHeight + 12.dp
    val top = if (showProfile) 310.dp else 110.dp; val n = maxOf(4, ((h - top) / ch).toInt())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topInsetDp, bottom = 72.dp)) { item("top") { LibraryTopSkeleton(showProfile) }; items(n) { SkeletonCard() } }
}

@Composable
private fun SpecialsSkeleton(topInsetDp: androidx.compose.ui.unit.Dp) {
    val h = with(androidx.compose.ui.platform.LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
    val ch = DesignTokens.CoverHeight + 12.dp
    val n = maxOf(6, ((h + topInsetDp) / ch).toInt())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topInsetDp, bottom = 72.dp)) { items(n) { SkeletonCard() } }
}

// ────────── 记录页 ──────────

@Composable
private fun ActivityPage(
    listState: LazyListState,
    libraryViewModel: LibraryViewModel,
    bangumiViewModel: BangumiViewModel,
    onNavigateToDetail: (Int, String, String) -> Unit,
    onNavigateToBangumiDetail: (Int, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val steamLoading by libraryViewModel.loading.observeAsState()
    val bangumiLoading by bangumiViewModel.loading.observeAsState()
    val revision by ActivityStats.revision.observeAsState()
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val today = remember { activityDateString(Calendar.getInstance()) }
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var selectedDate by remember { mutableStateOf(today) }
    var yearStats by remember { mutableStateOf<Map<String, DailyActivity>>(emptyMap()) }
    var availableYears by remember { mutableStateOf(setOf(currentYear)) }
    var baselineOnly by remember { mutableStateOf(false) }

    LaunchedEffect(revision, selectedYear) {
        val snapshot = withContext(Dispatchers.IO) {
            ActivityStats.getYearSnapshot(context, selectedYear)
        }
        yearStats = snapshot.stats
        availableYears = snapshot.availableYears + currentYear
        baselineOnly = snapshot.baselineOnly
        if (!selectedDate.startsWith("$selectedYear-")) {
            selectedDate = if (selectedYear == currentYear) {
                today
            } else {
                yearStats.keys.maxOrNull() ?: "$selectedYear-12-31"
            }
        }
    }

    val selectedDay = yearStats[selectedDate]
        ?: DailyActivity(selectedDate, 0, 0, emptyList())
    val minYear = availableYears.minOrNull() ?: currentYear
    val apiKey = UserPrefs.getApiKey(context)
    val steamId = UserPrefs.getSteamId(context)
    val bgmUsername = UserPrefs.getBangumiUsername(context)
    val bgmAccessToken = UserPrefs.getBangumiAccessToken(context)
    val topBarInsetDp = 48.dp + statusBarHeightDp() + 4.dp

    PullToRefresh(
        isRefreshing = steamLoading == true || bangumiLoading == true,
        onRefresh = {
            libraryViewModel.refresh(apiKey, steamId, context)
            if (
                ThemeUtils.isBangumiEnabled(context) &&
                bgmUsername.isNotBlank() &&
                bgmAccessToken.isNotBlank()
            ) {
                bangumiViewModel.refresh(bgmUsername, bgmAccessToken, context)
            }
        },
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topBarInsetDp),
        refreshTexts = listOf(
            context.getString(R.string.general_pull_down),
            context.getString(R.string.general_release),
            context.getString(R.string.general_refreshing),
            context.getString(R.string.general_refreshed)
        )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            item("activity_top_spacer") { Spacer(Modifier.height(topBarInsetDp)) }
            item("activity_year") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = { selectedYear-- },
                        enabled = selectedYear > minYear
                    ) {
                        Image(
                            imageVector = MiuixIcons.Light.ChevronBackward,
                            contentDescription = context.getString(R.string.activity_previous_year),
                            modifier = Modifier.size(DesignTokens.IconLg),
                            colorFilter = ColorFilter.tint(
                                MiuixTheme.colorScheme.onSurface.copy(
                                    alpha = if (selectedYear > minYear) 1f else DesignTokens.OpacityInactive
                                )
                            )
                        )
                    }
                    Text(
                        text = selectedYear.toString(),
                        modifier = Modifier.padding(horizontal = 24.dp),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = DesignTokens.AccentBlue
                    )
                    IconButton(
                        onClick = { selectedYear++ },
                        enabled = selectedYear < currentYear
                    ) {
                        Image(
                            imageVector = MiuixIcons.Light.ChevronForward,
                            contentDescription = context.getString(R.string.activity_next_year),
                            modifier = Modifier.size(DesignTokens.IconLg),
                            colorFilter = ColorFilter.tint(
                                MiuixTheme.colorScheme.onSurface.copy(
                                    alpha = if (selectedYear < currentYear) 1f else DesignTokens.OpacityInactive
                                )
                            )
                        )
                    }
                }
            }
            item("activity_heatmap") {
                ActivityHeatmap(
                    year = selectedYear,
                    today = today,
                    selectedDate = selectedDate,
                    stats = yearStats,
                    onDateSelected = { selectedDate = it }
                )
            }
            item("activity_summary") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = selectedDate,
                        fontSize = DesignTokens.TextSubtitle.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (selectedDay.entries.isEmpty()) {
                item("activity_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(
                                if (baselineOnly) {
                                    R.string.activity_baseline_ready
                                } else {
                                    R.string.activity_empty
                                }
                            ),
                            fontSize = DesignTokens.TextBody1.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                        )
                    }
                }
            } else {
                val rows = selectedDay.entries.chunked(4)
                items(
                    count = rows.size,
                    key = { rowIndex ->
                        rows[rowIndex].joinToString(
                            prefix = "activity_cover_row_",
                            separator = "_"
                        ) { "${it.kind.name}_${it.id}" }
                    }
                ) { rowIndex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rows[rowIndex].forEach { entry ->
                            ActivityCover(
                                entry = entry,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when (entry.kind) {
                                        ActivityKind.GAME ->
                                            onNavigateToDetail(entry.id, entry.title, "")
                                        ActivityKind.ANIME ->
                                            onNavigateToBangumiDetail(
                                                entry.id,
                                                entry.title,
                                                entry.secondaryTitle,
                                                entry.imageUrl
                                            )
                                    }
                                }
                            )
                        }
                        repeat(4 - rows[rowIndex].size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityHeatmap(
    year: Int,
    today: String,
    selectedDate: String,
    stats: Map<String, DailyActivity>,
    onDateSelected: (String) -> Unit
) {
    val dates = remember(year) { activityDatesForYear(year) }
    val rows = remember(dates) { (dates.size + 24) / 25 }
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gapPx = with(density) { 2.dp.toPx() }
    val cornerPx = with(density) { 3.dp.toPx() }
    val borderPx = with(density) { DesignTokens.BorderThick.toPx() }
    val emptyColor = MiuixTheme.colorScheme.surfaceVariant
    val selectedBorderColor = MiuixTheme.colorScheme.onSurface
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .aspectRatio(25f / rows)
            .onSizeChanged { canvasSize = it }
            .semantics {
                contentDescription =
                    context.getString(R.string.activity_heatmap_description, year)
            }
            .pointerInput(dates, today, canvasSize) {
                detectTapGestures { offset ->
                    if (canvasSize.width <= 0 || canvasSize.height <= 0) return@detectTapGestures
                    val column = (offset.x / (canvasSize.width / 25f)).toInt()
                    val row = (offset.y / (canvasSize.height / rows.toFloat())).toInt()
                    val index = row * 25 + column
                    val date = dates.getOrNull(index) ?: return@detectTapGestures
                    if (date <= today) onDateSelected(date)
                }
            }
    ) {
        val slotWidth = size.width / 25f
        val slotHeight = size.height / rows
        val cellSize = minOf(slotWidth, slotHeight) - gapPx
        dates.forEachIndexed { index, date ->
            val column = index % 25
            val row = index / 25
            val score = stats[date]?.score ?: 0.0
            val cellColor = when {
                score <= 0.0 -> emptyColor
                score <= 2.0 -> DesignTokens.AccentBlue.copy(alpha = 0.32f)
                score <= 4.0 -> DesignTokens.AccentBlue.copy(alpha = 0.52f)
                score <= 6.0 -> DesignTokens.AccentBlue.copy(alpha = 0.74f)
                else -> DesignTokens.AccentBlue
            }
            val topLeft = Offset(
                x = column * slotWidth + (slotWidth - cellSize) / 2,
                y = row * slotHeight + (slotHeight - cellSize) / 2
            )
            drawRoundRect(
                color = cellColor,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(cellSize, cellSize),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx)
            )
            if (date == selectedDate) {
                drawRoundRect(
                    color = selectedBorderColor,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(cellSize, cellSize),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = borderPx)
                )
            }
        }
    }
}

@Composable
private fun ActivityCover(entry: ActivityEntry, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    var imageModel by remember(entry.kind, entry.id, entry.imageUrl) {
        mutableStateOf<Any>(entry.imageUrl)
    }
    val description = context.getString(
        if (entry.kind == ActivityKind.GAME) {
            R.string.activity_game_cover
        } else {
            R.string.activity_anime_cover
        },
        entry.secondaryTitle.ifBlank { entry.title }
    )
    Box(
        modifier = modifier
            .aspectRatio(0.67f)
            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        val imageRequest = remember(imageModel, entry.kind, entry.id) {
            ImageRequest.Builder(context)
                .data(imageModel)
                .crossfade(true)
                .listener(
                    onError = { _, _ ->
                        if (entry.kind == ActivityKind.GAME && imageModel == entry.imageUrl) {
                            imageModel = buildHeaderUrl(context, entry.id)
                        }
                    }
                )
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = description,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

private fun activityDatesForYear(year: Int): List<String> {
    val calendar = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.DAY_OF_YEAR, 1)
    }
    val days = calendar.getActualMaximum(Calendar.DAY_OF_YEAR)
    return buildList(days) {
        repeat(days) {
            add(activityDateString(calendar))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}

private fun activityDateString(calendar: Calendar): String = String.format(
    Locale.US,
    "%04d-%02d-%02d",
    calendar.get(Calendar.YEAR),
    calendar.get(Calendar.MONTH) + 1,
    calendar.get(Calendar.DAY_OF_MONTH)
)

/** Bangumi 收藏类型 → 标签颜色 */
private val BANGUMI_TYPE_COLORS: Map<Int, Color> = mapOf(
    1 to Color(0xFF42A5F5),
    2 to Color(0xFFFF9800),
    3 to Color(0xFF66BB6A),
    4 to Color(0xFF9E9E9E),
    5 to Color(0xFFEF5350),
)

@Composable
private fun BangumiPage(
    listState: LazyListState,
    typeFilter: Int,
    onTypeFilterChange: (Int) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateToDetail: (Int, String, String, String) -> Unit,
    viewModel: BangumiViewModel
) {
    val context = LocalContext.current
    val displayStyle = ThemeUtils.getBangumiDisplayStyle(context)  // 0=list, 1=grid
    val ratingMode = UserPrefs.getBangumiRatingMode(context)
    val bgmUsername = UserPrefs.getBangumiUsername(context)
    val bgmAccessToken = UserPrefs.getBangumiAccessToken(context)
    val lifecycleOwner = LocalLifecycleOwner.current
    val loading by viewModel.loading.observeAsState()
    val error by viewModel.error.observeAsState()
    val collections by viewModel.collections.observeAsState()
    val bgmUser by viewModel.user.observeAsState()
    val bgmRatings by viewModel.ratings.observeAsState()
    val bgmEpisodeTotals by viewModel.episodeTotals.observeAsState()
    val bgmWatchedEpisodeCounts by viewModel.watchedEpisodeCounts.observeAsState()
    val ratingsMap = bgmRatings ?: emptyMap()
    val episodeTotalsMap = bgmEpisodeTotals ?: emptyMap()
    val watchedEpisodeCountsMap = bgmWatchedEpisodeCounts ?: emptyMap()

    LaunchedEffect(bgmUsername, bgmAccessToken) {
        viewModel.loadIfNeeded(bgmUsername, bgmAccessToken, context)
    }

    DisposableEffect(lifecycleOwner, bgmUsername) {
        val observer = LifecycleEventObserver { _, event ->
            if (
                event == Lifecycle.Event.ON_RESUME &&
                BangumiViewModel.collectionChanged &&
                bgmUsername.isNotBlank()
            ) {
                BangumiViewModel.collectionChanged = false
                viewModel.refresh(bgmUsername, bgmAccessToken, context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 错误提示
    LaunchedEffect(error) {
        val (resId, arg) = error ?: return@LaunchedEffect
        val message = if (arg == null) context.getString(resId) else context.getString(resId, arg)
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        viewModel.clearError()
    }

    val topBarInsetDp = 48.dp + statusBarHeightDp() + 4.dp

    // ── 未填写用户名 ──
    if (bgmUsername.isBlank()) {
        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    imageVector = MiuixIcons.Light.Album,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody))
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = context.getString(R.string.bangumi_no_username),
                    fontSize = DesignTokens.TextBody1.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                )
            }
        }
        return
    }

    PullToRefresh(
        isRefreshing = loading == true,
        onRefresh = { viewModel.refresh(bgmUsername, bgmAccessToken, context) },
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topBarInsetDp),
        refreshTexts = listOf(
            context.getString(R.string.general_pull_down),
            context.getString(R.string.general_release),
            context.getString(R.string.general_refreshing),
            context.getString(R.string.general_refreshed)
        ),
    ) {
        val collectionMap = collections
        val showSkeleton = loading == true && collectionMap == null
        val showEmpty = !showSkeleton && (collectionMap == null || collectionMap.isEmpty())

        // ── 骨架屏数量 ──
        val windowHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height.toDp()
        }
        val skeletonCount = remember(windowHeight) {
            maxOf(4, ((windowHeight - 260.dp) / 132.dp).toInt())
        }
        // 骨架材质（@Composable，在此处初始化供 LazyColumn 使用）
        val shimmer = shimmerBrush()
        val skelBg = MiuixTheme.colorScheme.secondaryContainer

        // ── 状态变量 ──
        val allItems = remember(collectionMap) {
            collectionMap?.entries?.flatMap { (type, list) -> list.map { Pair(it, type) } } ?: emptyList()
        }
        val filteredItems = remember(allItems, typeFilter, searchQuery) {
            var list = allItems.filter { it.second == typeFilter }
            if (searchQuery.isNotBlank()) {
                list = list.filter { (item, _) ->
                    val sub = item.subject
                    (sub?.name_cn?.contains(searchQuery, ignoreCase = true) == true) ||
                    (sub?.name?.contains(searchQuery, ignoreCase = true) == true)
                }
            }
            list
        }
        val totalCount = collectionMap?.values?.sumOf { it.size } ?: 0

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            item("top_spacer") { Spacer(Modifier.height(topBarInsetDp)) }

            if (showSkeleton) {
                // ── 加载骨架 ──
                // 个人资料卡骨架
                item("skel_profile") {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(DesignTokens.CornerXLarge)).background(skelBg)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(70.dp).clip(CircleShape).background(shimmer))
                                Spacer(Modifier.width(24.dp))
                                Column(Modifier.weight(1f)) {
                                    Box(Modifier.fillMaxWidth(0.45f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                                    Spacer(Modifier.height(8.dp))
                                    Box(Modifier.fillMaxWidth(0.25f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                repeat(5) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.size(24.dp, 14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                                        Spacer(Modifier.height(6.dp))
                                        Box(Modifier.size(32.dp, 10.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                                    }
                                }
                            }
                        }
                    }
                }
                // 搜索栏骨架
                item("skel_search") {
                    Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 18.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                        .background(MiuixTheme.colorScheme.surfaceVariant))
                }
                // 筛选条骨架
                item("skel_filters") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(5) {
                            Box(Modifier.size(56.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(shimmer))
                        }
                    }
                }
                // 条目骨架（适配动漫页头部高度）
                val n2 = skeletonCount
                items(n2) { BangumiSkeletonCard() }
            } else if (showEmpty) {
                // ── 空状态 ──
                item("empty") {
                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = context.getString(R.string.bangumi_empty),
                            fontSize = DesignTokens.TextBody1.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                        )
                    }
                }
            } else {
                // ── 个人资料卡 ──
                item("bgm_profile") { BangumiProfileCard(bgmUsername, bgmUser, totalCount, collectionMap!!) }

                // ── 搜索栏 ──
                item("search_bar") {
                    var textFieldValue by remember(searchQuery) { mutableStateOf(searchQuery) }
                    TextField(
                        value = textFieldValue,
                        onValueChange = { v -> textFieldValue = v; onSearchQueryChange(v.trim()) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                        label = context.getString(R.string.bangumi_search),
                        useLabelAsPlaceholder = true,
                        singleLine = true
                    )
                }

                // ── 收藏类型筛选横条 ──
                item("type_filter") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                        BangumiViewModel.typeNames.forEach { (type, nameRes) ->
                            MarkFilterChip(
                                label = context.getString(nameRes),
                                selected = typeFilter == type,
                                color = BANGUMI_TYPE_COLORS[type],
                                onClick = { onTypeFilterChange(if (typeFilter == type) 2 else type) }
                            )
                        }
                    }
                    }
                }

                // ── 动漫列表 ──
                if (displayStyle == 1) {
                    // 网格模式：每行4个封面
                    val chunked = filteredItems.chunked(4)
                    items(
                        count = chunked.size,
                        key = { rowIndex ->
                            chunked[rowIndex].joinToString(
                                prefix = "grid_row_",
                                separator = "_"
                            ) { it.first.subject_id.toString() }
                        }
                    ) { rowIndex ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunked[rowIndex].forEach { (item, _) ->
                                val sub = item.subject
                                BangumiGridItem(
                                    imageUrl = sub?.images?.common ?: sub?.images?.medium,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        onNavigateToDetail(item.subject_id,
                                            sub?.name ?: "",
                                            sub?.name_cn ?: "",
                                            sub?.images?.large ?: "")
                                    }
                                )
                            }
                            // 填充空位
                            repeat(4 - chunked[rowIndex].size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    // 列表模式（扁平，横线分割）
                    itemsIndexed(filteredItems, key = { _, pair -> "bgm_${pair.first.subject_id}" }) { index, (item, type) ->
                        if (index > 0) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp))
                        }
                        BangumiItem(
                            item = item,
                            type = type,
                            ratingMode = ratingMode,
                            ratings = ratingsMap,
                            episodeTotals = episodeTotalsMap,
                            watchedEpisodeCounts = watchedEpisodeCountsMap,
                            onClick = {
                            onNavigateToDetail(item.subject_id,
                                item.subject?.name ?: "",
                                item.subject?.name_cn ?: "",
                                item.subject?.images?.large ?: "")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BangumiItem(
    item: BangumiCollection,
    type: Int,
    ratingMode: Int,
    ratings: Map<Int, Any?>,
    episodeTotals: Map<Int, Int>,
    watchedEpisodeCounts: Map<Int, Int>,
    onClick: () -> Unit
) {
    val sub = item.subject ?: return
    val context = LocalContext.current
    val imgUrl = sub.images?.common ?: sub.images?.medium
    val coverW = 80.dp; val coverH = 112.dp
    val dim = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
    val displayTags = item.tags.orEmpty()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .ifEmpty {
            sub.tags.orEmpty()
                .map { it.name.trim() }
                .filter(String::isNotEmpty)
        }
        .distinctBy { it.lowercase() }
        .take(6)
    val episodeTotal = episodeTotals[item.subject_id]
        ?: sub.eps?.takeIf { it > 0 }
    val watchedEpisodeCount = watchedEpisodeCounts[item.subject_id]
        ?: item.ep_status.coerceAtMost(episodeTotal ?: item.ep_status)
    val episodeText = when {
        type == 1 && episodeTotal != null ->
            context.getString(R.string.bangumi_episode_total, episodeTotal)
        type != 1 && watchedEpisodeCount > 0 ->
            if (episodeTotal != null) {
                context.getString(
                    R.string.bangumi_card_episode_progress,
                    watchedEpisodeCount,
                    episodeTotal
                )
            } else {
                context.getString(R.string.bangumi_card_episode_seen, watchedEpisodeCount)
            }
        type != 1 && episodeTotal != null ->
            context.getString(R.string.bangumi_card_episode_count, episodeTotal)
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 左：封面
        Box(
            Modifier.size(coverW, coverH)
                .clip(RoundedCornerShape(6.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant)
        ) {
            if (imgUrl != null) {
                AsyncImage(
                    model = imgUrl,
                    contentDescription = sub.name_cn ?: sub.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // 中：名字 + 话数/进度
        Column(Modifier.weight(1f).height(coverH).padding(vertical = 2.dp)) {
            Text(
                text = sub.name_cn ?: sub.name,
                fontSize = DesignTokens.TextBody1.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (displayTags.isNotEmpty()) {
                Spacer(Modifier.height(DesignTokens.SpaceMd))
                displayTags.chunked(3).forEachIndexed { rowIndex, rowTags ->
                    if (rowIndex > 0) Spacer(Modifier.height(DesignTokens.SpaceXs))
                    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpaceXs)) {
                        rowTags.forEach { tag ->
                            Text(
                                text = tag,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                                    .background(MiuixTheme.colorScheme.secondaryContainer)
                                    .padding(
                                        horizontal = DesignTokens.SpaceXs,
                                        vertical = DesignTokens.SpaceXxs
                                    ),
                                color = dim,
                                fontSize = DesignTokens.TextCaption.sp,
                                softWrap = false
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (episodeText != null) {
                Text(text = episodeText, fontSize = 12.sp, color = dim)
            }
        }
        // 右：评分 + 评级 + 更新日期
        fun extractScore(r: Any?): Double? = when (r) {
            is Map<*, *> -> {
                val s = r["score"]
                when (s) { is Double -> s; is Number -> s.toDouble(); else -> null }
            }
            else -> null
        }
        val myRate = if (item.rate > 0) item.rate.toDouble() else null
        val globalScore: Double? = extractScore(ratings[item.subject_id]) ?: extractScore(sub.rating)
        val score: Double? = when (ratingMode) {
            1 -> myRate          // 仅我的评分
            2 -> null            // 不展示
            else -> globalScore  // 展示评分（默认）
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.height(coverH).padding(vertical = 2.dp), horizontalAlignment = Alignment.End) {
            if (score != null && score > 0) {
                val scoreText = if (ratingMode == 1) "${item.rate}" else String.format("%.1f", score)
                Text(text = scoreText, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, color = bangumiScoreColor(score))
                Spacer(Modifier.height(2.dp))
                Text(text = bangumiGrade(score), fontSize = 10.sp, color = dim)
            }
            Spacer(Modifier.weight(1f))
            val updated = item.updated_at?.take(10)
            if (!updated.isNullOrBlank()) {
                Text(text = updated, fontSize = 11.sp, color = dim.copy(alpha = 0.7f))
            }
        }
    }
}

/** 网格模式封面（仅封面，无文字） */
@Composable
private fun BangumiGridItem(imageUrl: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val aspectRatio = 0.7f  // 标准海报比例
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(6.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .noRippleClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/** Bangumi 评分 → 中文评级 */
private fun bangumiGrade(score: Double): String = when {
    score >= 9.0 -> "超神作"
    score >= 8.0 -> "神作"
    score >= 7.0 -> "力荐"
    score >= 6.0 -> "推荐"
    score >= 5.0 -> "还行"
    score >= 4.0 -> "较差"
    else -> "差"
}

/** Bangumi 评分 → 颜色分级 */
private fun bangumiScoreColor(score: Double): Color = when {
    score >= 8.0 -> Color(0xFFE53935)
    score >= 7.0 -> Color(0xFFFF9800)
    score >= 5.0 -> Color(0xFF42A5F5)
    else -> Color(0xFF9E9E9E)
}

// ── 动漫个人资料卡 ──
@Composable
private fun BangumiProfileCard(username: String, user: BangumiUser?, totalCount: Int, collectionMap: Map<Int, List<BangumiCollection>>) {
    val context = LocalContext.current
    // Bangumi 用户头像（medium 尺寸，56dp 圆形 — 对齐游戏页 AvatarInner）
    val avatarUrl = user?.avatar?.medium ?: user?.avatar?.small
    val displayName = user?.nickname?.ifBlank { null } ?: "@$username"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .background(MiuixTheme.colorScheme.secondaryContainer, RoundedCornerShape(DesignTokens.CornerXLarge))
            .clip(RoundedCornerShape(DesignTokens.CornerXLarge))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp)) {
            // 头像 + 用户名行
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 头像（圆形，56dp — 对齐游戏页 AvatarInner）
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            imageVector = MiuixIcons.Light.Album,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody))
                        )
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column {
                    Text(
                        text = displayName,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = DesignTokens.TextHeadline.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = context.getString(R.string.bangumi_profile_total, totalCount),
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                        fontSize = DesignTokens.TextBody2.sp
                    )
                }
            }

            // 分类统计行
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BangumiViewModel.typeNames.forEach { (type, nameRes) ->
                    val count = collectionMap[type]?.size ?: 0
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$count",
                            color = MiuixTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = DesignTokens.TextSubtitle.sp
                        )
                        Text(
                            text = context.getString(nameRes),
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                            fontSize = DesignTokens.TextCaption.sp
                        )
                    }
                }
            }
        }
    }
}
