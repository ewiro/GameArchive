package com.example.gamearchive

import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.icon.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

internal const val PORTRAIT_COVER_ASPECT_RATIO = 2f / 3f
internal val BANGUMI_GRID_HORIZONTAL_PADDING = 18.dp
internal val BANGUMI_GRID_ITEM_SPACING = 8.dp
internal val BANGUMI_GRID_COVER_CORNER = 6.dp

@Composable
private fun ActivityTopBarIcon(
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        }
    ) {
        val iconSize = size.minDimension
        val strokeWidth = iconSize * 0.095f
        val radius = iconSize * 0.41f
        drawCircle(
            color = color,
            radius = radius,
            style = Stroke(width = strokeWidth)
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x, center.y - radius * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x + radius * 0.5f, center.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CompactPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable (Modifier) -> Unit,
) {
    val state = rememberPullToRefreshState()
    val textSpaceProgress by animateFloatAsState(
        targetValue = when (state.refreshState) {
            RefreshState.Idle, RefreshState.RefreshComplete -> 0f
            else -> state.pullProgress
        },
        animationSpec = if (state.refreshState == RefreshState.RefreshComplete) {
            tween(durationMillis = 200, easing = CubicBezierEasing(0f, 0f, 0f, 0.37f))
        } else {
            snap()
        },
        label = "pull_refresh_text_space"
    )

    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        pullToRefreshState = state,
        contentPadding = contentPadding,
        circleSize = PullToRefreshDefaults.circleSize * (2f / 3f),
        refreshTexts = emptyList(),
    ) {
        content(Modifier.offset(y = -(36.dp * textSpaceProgress)))
    }
}

@Composable
@Suppress("DEPRECATION")
internal fun MainScreen() {
    val context = LocalContext.current
    val specialsEnabled = ThemeUtils.isSpecialsEnabled(context)
    val bangumiEnabled = ThemeUtils.isBangumiEnabled(context)
    val activityEnabled = ThemeUtils.isActivityEnabled(context)
    val bangumiPage = if (bangumiEnabled) 1 else -1
    val pageCount = 1 + (if (bangumiEnabled) 1 else 0)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    var showSpecialsPage by remember { mutableStateOf(false) }
    var showActivityPage by remember { mutableStateOf(false) }
    var bottomBarVisible by remember { mutableStateOf(true) }
    val selectedTab = pagerState.currentPage
    val displayStyle = ThemeUtils.getDisplayStyle(context)
    val coverMotion = rememberCoverMotion(
        enabled = !showSpecialsPage &&
            (
                showActivityPage ||
                    selectedTab == 0 &&
                    (UserPrefs.isShowProfile(context) || displayStyle == 1) ||
                    selectedTab == bangumiPage
                )
    )
    val bangumiProfileBackgroundFile = remember(bangumiEnabled) {
        BangumiProfileBackground.file(context).takeIf { it.isFile }
    }
    val isImmersiveLibraryHeader = !showSpecialsPage && !showActivityPage &&
        selectedTab == 0 && UserPrefs.isShowProfile(context)
    val isImmersiveBangumiHeader =
        !showSpecialsPage && !showActivityPage && selectedTab == bangumiPage
    val isImmersiveProfileHeader =
        isImmersiveLibraryHeader || isImmersiveBangumiHeader
    val immersiveHeaderUsesDarkBackground =
        isImmersiveLibraryHeader ||
            (isImmersiveBangumiHeader && bangumiProfileBackgroundFile != null)
    val topBarContentColor = if (immersiveHeaderUsesDarkBackground) {
        Color.White
    } else {
        MiuixTheme.colorScheme.onSurface
    }
    val mainLifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(
        mainLifecycleOwner,
        isImmersiveProfileHeader,
        immersiveHeaderUsesDarkBackground
    ) {
        val activity = context as? android.app.Activity
        fun updateStatusBarAppearance() {
            if (activity == null) return
            if (immersiveHeaderUsesDarkBackground) {
                WindowCompat.getInsetsController(
                    activity.window,
                    activity.window.decorView
                ).isAppearanceLightStatusBars = false
            } else {
                ThemeUtils.applyStatusBarAppearance(activity)
            }
        }
        updateStatusBarAppearance()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) updateStatusBarAppearance()
        }
        mainLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            mainLifecycleOwner.lifecycle.removeObserver(observer)
            activity?.let(ThemeUtils::applyStatusBarAppearance)
        }
    }

    val libraryViewModel: LibraryViewModel = viewModel()
    val bangumiViewModel: BangumiViewModel = viewModel()
    val libraryListState = rememberLazyListState()
    val specialsListState = rememberLazyListState()
    val bangumiListState = rememberLazyListState()
    val activityListState = rememberLazyListState()
    var bangumiTypeFilter by remember { mutableIntStateOf(2) }  // 默认在看
    var bangumiSearchQuery by remember { mutableStateOf("") }
    val activeListState = if (showSpecialsPage) {
        specialsListState
    } else if (showActivityPage) {
        activityListState
    } else when (selectedTab) {
        0 -> libraryListState
        bangumiPage -> bangumiListState
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
        snapshotFlow { activeListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                bottomBarVisible = index == 0
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
    BackHandler(enabled = showSpecialsPage) {
        showSpecialsPage = false
    }
    BackHandler(enabled = showActivityPage) {
        showActivityPage = false
    }

    // 状态栏高度（顶栏叠加层需要）
    val statusBarDp = statusBarHeightDp()
    val topBarHeightDp = 48.dp + statusBarDp + 4.dp

    // 排序弹窗状态（从 SpecialsScreen 提升上来）
    var showSortDialog by remember { mutableStateOf(false) }
    var bangumiTransitionLaunching by remember { mutableStateOf(false) }
    DisposableEffect(mainLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                bangumiTransitionLaunching = false
            }
        }
        mainLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { mainLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val navigateToDetail: (Int, String, String) -> Unit = { appId, name, price ->
        context.startActivity(Intent(context, DetailActivity::class.java).apply {
            putExtra("APP_ID", appId)
            putExtra("APP_NAME", name)
            putExtra("APP_PRICE", price)
        })
        (context as? android.app.Activity)?.overridePendingTransition(
            R.anim.slide_in_right,
            R.anim.slide_out_left
        )
    }
    val navigateToBangumiDetail: (Int, String, String, String) -> Unit =
        { id, name, nameCn, imageUrl ->
            context.startActivity(Intent(context, BangumiDetailActivity::class.java).apply {
                putExtra("SUBJECT_ID", id)
                putExtra("SUBJECT_NAME", name)
                putExtra("SUBJECT_NAME_CN", nameCn)
                putExtra("SUBJECT_IMAGE", imageUrl)
            })
        }
    val navigateToBangumiDetailWithCover: (Int, String, String, String, Rect, ImageBitmap) -> Unit =
        { id, name, nameCn, imageUrl, sourceRect, imageBitmap ->
            if (!bangumiTransitionLaunching) {
                bangumiTransitionLaunching = true
                BangumiCoverTransitionStore.begin(id, imageBitmap, sourceRect)
                try {
                    context.startActivity(
                        Intent(context, BangumiCoverTransitionActivity::class.java).apply {
                            putExtra("SUBJECT_ID", id)
                            putExtra("SUBJECT_NAME", name)
                            putExtra("SUBJECT_NAME_CN", nameCn)
                            putExtra("SUBJECT_IMAGE", imageUrl)
                        }
                    )
                    (context as? android.app.Activity)?.overridePendingTransition(0, 0)
                } catch (error: Exception) {
                    BangumiCoverTransitionStore.clear(id)
                    bangumiTransitionLaunching = false
                    throw error
                }
            }
        }

    // 动画偏移量
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bottomBarOffsetY by animateDpAsState(
        targetValue = if (bottomBarVisible) 0.dp else 60.dp,
        animationSpec = tween(durationMillis = DesignTokens.AnimDuration, easing = FastOutSlowInEasing)
    )

    Surface(modifier = Modifier.fillMaxSize()) {
    val blurSupported = isRuntimeShaderSupported()
    val pageBackgroundColor = MiuixTheme.colorScheme.background
    val bottomBarBackdrop = if (blurSupported) {
        rememberLayerBackdrop {
            drawRect(pageBackgroundColor)
            drawContent()
        }
    } else {
        null
    }
    Box(modifier = Modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .then(
                if (bottomBarBackdrop != null) {
                    Modifier.layerBackdrop(bottomBarBackdrop)
                } else {
                    Modifier
                }
            )
    ) {

        // ── 内容层：特惠与记录独立打开，主分页仅包含游戏和动漫 ──
        if (showSpecialsPage) {
            SpecialsScreen(
                listState = specialsListState,
                showSortDialog = showSortDialog,
                onDismissSortDialog = { showSortDialog = false },
                onNavigateToDetail = navigateToDetail
            )
        } else if (showActivityPage) {
            ActivityPage(
                listState = activityListState,
                libraryViewModel = libraryViewModel,
                bangumiViewModel = bangumiViewModel,
                coverMotion = coverMotion,
                includeAnime = bangumiEnabled,
                onNavigateToDetail = navigateToDetail,
                onNavigateToBangumiDetail = navigateToBangumiDetail
            )
        } else HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true
        ) { page ->
            when (page) {
                0 -> LibraryScreen(
                    listState = libraryListState,
                    coverMotion = coverMotion,
                    showBottomBar = bangumiEnabled,
                    onNavigateToDetail = navigateToDetail,
                    onNavigateToSettings = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                    viewModel = libraryViewModel
                )
                else -> BangumiPage(
                    listState = bangumiListState,
                    coverMotion = coverMotion,
                    typeFilter = bangumiTypeFilter,
                    onTypeFilterChange = { bangumiTypeFilter = it },
                    searchQuery = bangumiSearchQuery,
                    onSearchQueryChange = { bangumiSearchQuery = it },
                    onNavigateToDetail = navigateToBangumiDetail,
                    onNavigateToDetailWithCover = navigateToBangumiDetailWithCover,
                    profileBackgroundFile = bangumiProfileBackgroundFile,
                    viewModel = bangumiViewModel
                )
            }
        }

        // ── 顶栏叠加层 ──
        val topBarModifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .scrollLinkedTopBar(activeListState, topBarHeightDp)
            .graphicsLayer {
                alpha = 0.999f
            }
        val topBarContent: @Composable () -> Unit = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp)
                    .padding(top = statusBarDp + 4.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!showSpecialsPage && !showActivityPage && selectedTab == bangumiPage) {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, BangumiSearchActivity::class.java))
                    }) {
                        Image(
                            imageVector = MiuixIcons.Demibold.Search,
                            contentDescription = stringResource(R.string.bangumi_search_title),
                            modifier = Modifier.size(DesignTokens.IconXl),
                            colorFilter = ColorFilter.tint(topBarContentColor)
                        )
                    }
                }
                if (!showSpecialsPage && !showActivityPage && selectedTab == 0) {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, WishlistActivity::class.java))
                        (context as? android.app.Activity)?.overridePendingTransition(
                            R.anim.slide_in_right,
                            R.anim.slide_out_left
                        )
                    }) {
                        Image(
                            imageVector = MiuixIcons.Demibold.Favorites,
                            contentDescription = stringResource(R.string.wishlist_title),
                            modifier = Modifier.size(DesignTokens.IconXl),
                            colorFilter = ColorFilter.tint(topBarContentColor)
                        )
                    }
                    if (specialsEnabled) {
                        IconButton(onClick = { showSpecialsPage = true }) {
                            Image(
                                imageVector = MiuixIcons.Demibold.Promotions,
                                contentDescription = stringResource(R.string.nav_specials),
                                modifier = Modifier.size(DesignTokens.IconXl),
                                colorFilter = ColorFilter.tint(topBarContentColor)
                            )
                        }
                    }
                }
                if (!showSpecialsPage) {
                    Spacer(Modifier.weight(1f))
                } else {
                    Text(
                        text = stringResource(R.string.nav_specials),
                        fontWeight = FontWeight.Bold,
                        fontSize = DesignTokens.TextTitle.sp,
                        color = topBarContentColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!showSpecialsPage && !showActivityPage) {
                    if (activityEnabled) {
                        IconButton(onClick = { showActivityPage = true }) {
                            ActivityTopBarIcon(
                                color = topBarContentColor,
                                contentDescription = stringResource(R.string.nav_activity),
                                modifier = Modifier.size(DesignTokens.IconXl)
                            )
                        }
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Image(
                            imageVector = MiuixIcons.Demibold.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            modifier = Modifier.size(DesignTokens.IconXl),
                            colorFilter = ColorFilter.tint(topBarContentColor)
                        )
                    }
                } else if (showSpecialsPage) {
                    IconButton(onClick = { showSortDialog = true }) {
                        Image(
                            imageVector = MiuixIcons.Demibold.Filter,
                            contentDescription = stringResource(R.string.general_sort),
                            modifier = Modifier.size(DesignTokens.IconXl),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                        )
                    }
                }
            }
        }
        if (isImmersiveProfileHeader) {
            Box(modifier = topBarModifier) {
                topBarContent()
            }
        } else {
            Surface(modifier = topBarModifier) {
                topBarContent()
            }
        }
    }

        // ── 底栏叠加层（仅游戏和动漫主页显示） ──
        if (!showSpecialsPage && !showActivityPage && bangumiEnabled) {
        val bottomBarTint = MiuixTheme.colorScheme.surface.copy(
            alpha = if (isAppInDarkTheme()) 0.62f else 0.72f
        )
        val bottomBarShape = RoundedCornerShape(
            topStart = DesignTokens.CornerXLarge,
            topEnd = DesignTokens.CornerXLarge,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
        val bottomBarBlurColors = if (bottomBarBackdrop != null) {
            BlurDefaults.blurColors(
                blendColors = listOf(
                    BlendColorEntry(bottomBarTint, BlurBlendMode.SrcOver)
                ),
                saturation = 1.1f
            )
        } else {
            null
        }
        val bottomBarBackgroundModifier = if (bottomBarBackdrop != null) {
            Modifier.textureBlur(
                backdrop = bottomBarBackdrop,
                shape = bottomBarShape,
                blurRadius = 24f,
                colors = bottomBarBlurColors!!
            )
        } else {
            Modifier.background(bottomBarTint, bottomBarShape)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(DesignTokens.BottomBarHeight)
                .clickable(enabled = false, onClick = {})
                .graphicsLayer {
                    translationY = with(density) { bottomBarOffsetY.toPx() }
                    alpha = 0.999f
                }
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(bottomBarShape)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(bottomBarBackgroundModifier)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .blur(1.dp, BlurredEdgeTreatment.Unbounded)
                        .background(bottomBarTint)
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
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
                        contentDescription = stringResource(R.string.nav_library),
                        modifier = Modifier.size(DesignTokens.IconXl),
                        colorFilter = ColorFilter.tint(if (selectedTab == 0) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
                    )
                    Text(text = stringResource(R.string.nav_library), fontSize = DesignTokens.TextCaption.sp,
                        color = if (selectedTab == 0) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
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
                        contentDescription = stringResource(R.string.nav_bangumi),
                        modifier = Modifier.size(DesignTokens.IconXl),
                        colorFilter = ColorFilter.tint(if (selectedTab == bangumiPage) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
                    )
                    Text(text = stringResource(R.string.nav_bangumi), fontSize = DesignTokens.TextCaption.sp,
                        color = if (selectedTab == bangumiPage) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive))
                }
                }
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
    coverMotion: State<CoverMotion>,
    showBottomBar: Boolean,
    onNavigateToDetail: (Int, String, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val games = uiState.games
    val player = uiState.player
    val profileDecor = uiState.profileDecor
    val loading = uiState.isLoading
    val priceMap = uiState.prices

    val apiKey = UserPrefs.getApiKey(context)
    val steamId = UserPrefs.getSteamId(context)
    val showProfile = UserPrefs.isShowProfile(context)

    LaunchedEffect(Unit) { viewModel.loadIfNeeded(apiKey, steamId, context) }
    LaunchedEffect(
        player?.steamid,
        showProfile
    ) {
        if (showProfile) {
            player?.steamid?.let { id ->
                viewModel.loadProfileDecor(context, id)
            }
        }
    }

    val libraryError = uiState.error
    val libraryErrorMessage = libraryError?.let { (resId, arg) ->
        if (arg != null) stringResource(resId, arg) else stringResource(resId)
    }
    LaunchedEffect(libraryError, libraryErrorMessage) {
        libraryErrorMessage?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
        if (libraryError != null) viewModel.clearError()
    }

    val isGrouping = ThemeUtils.isGroupingEnabled(context)
    val displayStyle = ThemeUtils.getDisplayStyle(context)
    val showPlaytimeBackground = ThemeUtils.isPlaytimeBadgeBackgroundEnabled(context)
    val usePlaytimeBadgeTextColor = ThemeUtils.isPlaytimeBadgeTextColorEnabled(context)
    val sortMode = ThemeUtils.getSortMode(context)
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
    val resolvedGamePortraits = remember { mutableStateMapOf<Int, String>() }
    val resolvingGamePortraits = remember { mutableStateMapOf<Int, Boolean>() }
    val failedGamePortraits = remember { mutableStateMapOf<Int, Boolean>() }
    val portraitScope = rememberCoroutineScope()

    fun resolvePortrait(game: GameInfo) {
        if (resolvingGamePortraits[game.appid] == true ||
            resolvedGamePortraits.containsKey(game.appid) ||
            failedGamePortraits[game.appid] == true
        ) return
        resolvingGamePortraits[game.appid] = true
        portraitScope.launch {
            val portraitUrl = withContext(Dispatchers.IO) {
                readCachedPortraitUrl(context, game.appid)
                    ?: runCatchingCancellable { resolveSteamPortraitUrl(game.appid) }.getOrNull()
            }
            resolvingGamePortraits.remove(game.appid)
            if (portraitUrl != null) {
                cachePortraitUrl(context, game.appid, portraitUrl)
                resolvedGamePortraits[game.appid] = portraitUrl
                failedGamePortraits.remove(game.appid)
            } else {
                failedGamePortraits[game.appid] = true
            }
        }
    }

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
    val density = androidx.compose.ui.platform.LocalDensity.current
    val windowWidth = with(density) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val profileContentHeight = 88.2.dp
    val profileOverlapHeight = DesignTokens.CornerXLarge
    val minimumProfileBackgroundHeight =
        topBarInsetDp + profileContentHeight + profileOverlapHeight
    val profileBackgroundHeight = maxOf(
        minimumProfileBackgroundHeight,
        windowWidth / (16f / 9f) * 1.2f
    )
    val profileHeaderHeight = profileBackgroundHeight - profileOverlapHeight
    val profileAreaHeight = profileHeaderHeight - topBarInsetDp
    val profileContentGap = DesignTokens.SpaceMd
    val profileHeaderHeightPx = with(density) { profileHeaderHeight.toPx() }
    val topBarInsetPx = with(density) { topBarInsetDp.toPx() }
    val profileShimmer = rememberLoadingSkeletonBrush()
    val contentSheetShape = RoundedCornerShape(
        topStart = DesignTokens.CornerXLarge,
        topEnd = DesignTokens.CornerXLarge
    )

    Box(modifier = Modifier.fillMaxSize()) {
    if (showProfile) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scrollOffset = when (listState.firstVisibleItemIndex) {
                        0 -> listState.firstVisibleItemScrollOffset.toFloat()
                        1 -> topBarInsetPx + listState.firstVisibleItemScrollOffset
                        else -> profileHeaderHeightPx
                    }
                    translationY = -scrollOffset
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(profileBackgroundHeight)
                    .background(MiuixTheme.colorScheme.secondaryContainer)
            ) {
                if (loading != true) {
                    SteamProfileHeroBackground(
                        decor = profileDecor,
                        modifier = Modifier
                            .matchParentSize()
                            .profileGravityBackground(coverMotion)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(profileAreaHeight)
                        .offset(y = topBarInsetDp)
                        .padding(bottom = DesignTokens.SpaceXxs),
                    contentAlignment = Alignment.BottomStart
                ) {
                    if (loading == true) {
                        SteamProfileSkeleton(profileShimmer)
                    } else if (player != null) {
                        ProfileHeader(
                            player = player,
                            gameCount = gameList.size,
                            totalHours = gameList.sumOf { it.playtime_forever } / 60.0,
                            decor = profileDecor,
                            motion = coverMotion
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = profileHeaderHeight)
                    .clip(contentSheetShape)
                    .background(MiuixTheme.colorScheme.background)
            )
        }
    }

    Crossfade(loading == true, animationSpec = tween(250)) { showSkeleton ->
    if (showSkeleton) {
        LibraryLoadingSkeleton(
            topInset = topBarInsetDp,
            showProfile = showProfile,
            immersiveProfileSpace = if (showProfile) {
                profileAreaHeight + profileContentGap
            } else {
                0.dp
            },
            listState = listState
        )
    } else {
        CompactPullToRefresh(
            isRefreshing = false,
            onRefresh = { viewModel.refresh(apiKey, steamId, context); listRefreshTrigger++ },
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = if (showProfile) {
                    profileHeaderHeight + profileContentGap
                } else {
                    topBarInsetDp
                }
            ),
        ) { refreshContentModifier ->
        LazyColumn(
            state = listState,
            modifier = refreshContentModifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = if (showBottomBar) 72.dp else DesignTokens.SpaceXl
            )
        ) {
            item("top_spacer") { Spacer(Modifier.height(topBarInsetDp)) }
            if (showProfile) {
                item("profile_space") { Spacer(Modifier.height(profileAreaHeight)) }
                item("profile_content_gap") {
                    Spacer(Modifier.height(profileContentGap))
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
                    label = stringResource(R.string.library_search),
                    useLabelAsPlaceholder = true,
                    singleLine = true
                )
            }

            // 标记筛选横条
            item("mark_filter") {
                Column {
                    val markScrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .clipToBounds()
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(markScrollState)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "全部" 按钮
                        MarkFilterChip(
                            label = stringResource(R.string.mark_filter_all),
                            selected = markFilter == -1,
                            color = null,
                            onClick = { markFilter = -1 }
                        )
                        GameMarks.markResIds.forEach { resId ->
                            MarkFilterChip(
                                label = stringResource(resId),
                                selected = markFilter == resId,
                                color = androidx.compose.ui.graphics.Color(
                                    GameMarks.colorFor(resId)
                                ),
                                onClick = {
                                    markFilter = if (markFilter == resId) -1 else resId
                                }
                            )
                        }
                    }
                    HorizontalScrollEdgeFades(
                        canScrollBackward = markScrollState.canScrollBackward,
                        canScrollForward = markScrollState.canScrollForward,
                        edgeColor = MiuixTheme.colorScheme.background,
                        width = 24.dp
                    )
                    }
                    if (markFilter != -1) {
                        Text(
                            text = stringResource(R.string.library_filter_count, filteredGames.size),
                            fontSize = DesignTokens.TextBody2.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            if (displayStyle == 1) {
                val recent = filteredGames.filter { (it.playtime_2weeks ?: 0) > 0 }
                val recentIds = recent.mapTo(hashSetOf()) { it.appid }
                val played = filteredGames.filter {
                    it.playtime_forever > 0 && it.appid !in recentIds
                }
                val unplayed = filteredGames.filter { it.playtime_forever == 0 }
                val gridGames = recent + played + unplayed
                val rows = gridGames.chunked(3)
                items(
                    count = rows.size,
                    key = { rowIndex ->
                        rows[rowIndex].joinToString(
                            prefix = "library_grid_row_",
                            separator = "_"
                        ) { it.appid.toString() }
                    }
                ) { rowIndex ->
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rows[rowIndex].forEach { game ->
                            GameGridItem(
                                game = game,
                                modifier = Modifier.weight(1f),
                                coverMotion = coverMotion,
                                resolvedPortraitUrl = resolvedGamePortraits[game.appid],
                                useFallback = failedGamePortraits[game.appid] == true,
                                onResolvePortrait = { resolvePortrait(game) },
                                onPortraitError = {
                                    resolvedGamePortraits.remove(game.appid)
                                    failedGamePortraits[game.appid] = true
                                },
                                onClick = {
                                    onNavigateToDetail(
                                        game.appid,
                                        game.name,
                                        priceMap[game.appid] ?: "Free / Unknown"
                                    )
                                }
                            )
                        }
                        repeat(3 - rows[rowIndex].size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else if (!isGrouping || markFilter != -1) {
                items(filteredGames, key = { "g_${it.appid}" }) { game ->
                    GameItem(
                        game = game,
                        price = priceMap[game.appid] ?: "",
                        customName = nameSnapshot[game.appid],
                        markRes = markSnapshot[game.appid] ?: -1,
                        viewModel = viewModel,
                        showPlaytimeBackground = showPlaytimeBackground,
                        usePlaytimeBadgeTextColor = usePlaytimeBadgeTextColor,
                        modifier = Modifier.animateItem(),
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
                            title = stringResource(R.string.library_group_recent, recent.size),
                            expanded = recentExpanded,
                            onClick = { recentExpanded = !recentExpanded }
                        )
                    }
                    if (recentExpanded) {
                        items(recent, key = { "r_${it.appid}" }) { game ->
                            GameItem(game, priceMap[game.appid] ?: "", nameSnapshot[game.appid], markSnapshot[game.appid] ?: -1, viewModel, showPlaytimeBackground, usePlaytimeBadgeTextColor) { onNavigateToDetail(game.appid, game.name, priceMap[game.appid] ?: "Free / Unknown") }
                        }
                    }
                }
                if (played.isNotEmpty()) {
                    item("hdr_played") {
                        GroupHeader(
                            title = stringResource(R.string.library_group_played, played.size),
                            expanded = playedExpanded,
                            onClick = { playedExpanded = !playedExpanded }
                        )
                    }
                    if (playedExpanded) {
                        items(played, key = { "p_${it.appid}" }) { game ->
                            GameItem(game, priceMap[game.appid] ?: "", nameSnapshot[game.appid], markSnapshot[game.appid] ?: -1, viewModel, showPlaytimeBackground, usePlaytimeBadgeTextColor) { onNavigateToDetail(game.appid, game.name, priceMap[game.appid] ?: "Free / Unknown") }
                        }
                    }
                }
                if (unplayed.isNotEmpty()) {
                    item("hdr_unplayed") {
                        GroupHeader(
                            title = stringResource(R.string.library_group_unplayed, unplayed.size),
                            expanded = unplayedExpanded,
                            onClick = { unplayedExpanded = !unplayedExpanded }
                        )
                    }
                    if (unplayedExpanded) {
                        items(unplayed, key = { "u_${it.appid}" }) { game ->
                            GameItem(game, priceMap[game.appid] ?: "", nameSnapshot[game.appid], markSnapshot[game.appid] ?: -1, viewModel, showPlaytimeBackground, usePlaytimeBadgeTextColor) { onNavigateToDetail(game.appid, game.name, priceMap[game.appid] ?: "Free / Unknown") }
                        }
                    }
                }
            }
        }
        } // close PullToRefresh
    } // close if/else
    } // close Crossfade
    } // close root Box
}

/** 游戏页网格封面：沿用记录页的竖屏素材、三列铁牌动效与横图兜底。 */
@Composable
private fun GameGridItem(
    game: GameInfo,
    modifier: Modifier = Modifier,
    coverMotion: State<CoverMotion>,
    resolvedPortraitUrl: String?,
    useFallback: Boolean,
    onResolvePortrait: () -> Unit,
    onPortraitError: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cachedPortraitUrl = remember(game.appid) {
        readCachedPortraitUrl(context, game.appid)
    }
    LaunchedEffect(game.appid, resolvedPortraitUrl, cachedPortraitUrl, useFallback) {
        if (resolvedPortraitUrl == null && cachedPortraitUrl == null && !useFallback) {
            onResolvePortrait()
        }
    }
    val portraitUrl = resolvedPortraitUrl ?: cachedPortraitUrl
    val showingPortrait = !useFallback && portraitUrl != null
    val imageModel = when {
        useFallback -> buildHeaderUrl(context, game.appid)
        portraitUrl != null -> portraitUrl
        else -> null
    }
    val coverShape = RoundedCornerShape(DesignTokens.CornerMedium)
    Box(modifier = modifier.aspectRatio(PORTRAIT_COVER_ASPECT_RATIO)) {
        Box(
            Modifier
                .matchParentSize()
                .metallicCoverShadow(coverMotion, coverShape)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .metallicCoverTilt(coverMotion, coverShape)
                .clip(coverShape)
                .background(MiuixTheme.colorScheme.surfaceVariant)
                .motionClickable(pressedScale = 0.95f, onClick = onClick)
        ) {
            val imageRequest = remember(imageModel, game.appid, showingPortrait) {
                ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true)
                    .memoryCacheKey(
                        "${if (showingPortrait) "portrait" else "header"}_${game.appid}"
                    )
                    .listener(
                        onError = { _, _ ->
                            if (showingPortrait) onPortraitError()
                        }
                    )
                    .build()
            }
            if (imageModel != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = stringResource(R.string.activity_game_cover, game.name),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            MetallicCoverOverlay(
                motion = coverMotion,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ── 游玩时长徽章颜色 → DesignTokens.badgeColor() / badgeColorMap
// ── 状态颜色 → DesignTokens.Status*

@Composable
private fun SteamProfileHeroBackground(
    decor: SteamProfileDecor?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customBgUrl = UserPrefs.getCustomBgUrl(context)
    val automaticBackgroundDecor = decor?.takeIf {
        it.backgroundMp4Url != null || it.backgroundWebmUrl != null
    }
    val hasBackground = customBgUrl.isNotEmpty() || automaticBackgroundDecor != null

    Box(modifier) {
        if (customBgUrl.isNotEmpty()) {
            AsyncImage(
                model = customBgUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
        } else if (automaticBackgroundDecor != null) {
            SteamProfileBackground(
                decor = automaticBackgroundDecor,
                playMotion = true,
                modifier = Modifier.matchParentSize()
            )
        }
        if (hasBackground) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(DesignTokens.ScrimDark.copy(alpha = 0.15f))
            )
        }
    }
}

@Composable
private fun SteamProfileSkeleton(shimmer: androidx.compose.ui.graphics.Brush) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 5.4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 7.2.dp,
                    bottom = 7.2.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(78.dp).clip(CircleShape).background(shimmer))
                Spacer(Modifier.width(24.dp))
                Column(Modifier.width(140.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.72f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(DesignTokens.CornerSmall))
                            .background(shimmer)
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(DesignTokens.CornerSmall))
                            .background(shimmer)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    player: PlayerInfo,
    gameCount: Int,
    totalHours: Double,
    decor: SteamProfileDecor?,
    motion: State<CoverMotion>
) {
    val context = LocalContext.current
    val customFrameUrl = UserPrefs.getCustomFrameUrl(context)
    val customAvatar = UserPrefs.getCustomAvatarUrl(context)
    val automaticAvatar = SteamProfileDecorRepository.proxiedMediaUrl(decor?.avatarUrl)
    val automaticFrame = SteamProfileDecorRepository.proxiedMediaUrl(decor?.avatarFrameUrl)
    val avatarModel = customAvatar.ifBlank { automaticAvatar ?: player.avatarfull }
    val frameModel = customFrameUrl.ifBlank { automaticFrame.orEmpty() }
    val totalHoursText = if (totalHours < 0.05) {
        "0h"
    } else {
        String.format(Locale.ROOT, "%.1fh", totalHours)
    }
    val textShadow = androidx.compose.ui.graphics.Shadow(
        color = DesignTokens.TextShadowColor,
        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
        blurRadius = 2f
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 5.4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 7.2.dp,
                    bottom = 7.2.dp
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .profileGravityForeground(motion),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(78.dp)) {
                    AsyncImage(
                        model = avatarModel,
                        contentDescription = player.personaname,
                        modifier = Modifier
                            .size(65.dp)
                            .align(Alignment.Center),
                        contentScale = ContentScale.Crop
                    )
                    if (frameModel.isNotEmpty()) {
                        AsyncImage(
                            model = frameModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.widthIn(max = 190.dp)) {
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
                        text = stringResource(
                            R.string.profile_summary,
                            totalHoursText,
                            gameCount
                        ),
                        color = Color.White.copy(alpha = DesignTokens.OpacityBody),
                        fontSize = DesignTokens.TextBody2.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                    )
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
            .motionClickable(pressedScale = 0.985f, onClick = onClick)
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
private fun MarkFilterChip(
    label: String,
    selected: Boolean,
    color: Color?,
    count: Int? = null,
    onClick: () -> Unit
) {
    val textColor = if (selected && color != null) {
        color
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityEmphasis)
    }
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
            .motionClickable(pressedScale = 0.96f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = DesignTokens.TextBody2.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            count?.let {
                Spacer(Modifier.width(DesignTokens.SpaceXxs))
                Text(
                    text = it.toString(),
                    fontSize = DesignTokens.TextCaption.sp,
                    color = textColor
                )
            }
        }
    }
}

// ── 游戏卡片（库存） ──
@Composable
internal fun GameItem(
    game: GameInfo,
    price: String,
    customName: String?,
    markRes: Int,
    viewModel: LibraryViewModel,
    showPlaytimeBackground: Boolean,
    usePlaytimeBadgeTextColor: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val h = game.playtime_forever / 60.0
    val badgeColor = DesignTokens.badgeColor(h)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playtimeContentColor = if (showPlaytimeBackground) {
        Color.White
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
    }
    val playtimeTextColor = if (!showPlaytimeBackground && usePlaytimeBadgeTextColor) {
        badgeColor
    } else {
        playtimeContentColor
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .motionClickable(pressedScale = 0.985f, onClick = onClick)
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
                            runCatchingCancellable {
                                val resp = GameArchiveApp.apiService.getGameDetails(
                                    game.appid, l = LocaleHelper.currentApiLanguage
                                )
                                val realUrl = resp[game.appid.toString()]?.data?.header_image
                                if (!realUrl.isNullOrEmpty()) {
                                    cache.edit {putString(
                                            "header_${game.appid}",
                                            "$realUrl|${System.currentTimeMillis()}"
                                    )}
                                }
                            }
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
                            .then(
                                if (showPlaytimeBackground) {
                                    Modifier.background(
                                        badgeColor,
                                        RoundedCornerShape(DesignTokens.CornerMedium)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(
                                horizontal = if (showPlaytimeBackground) 9.dp else 0.dp,
                                vertical = 4.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_time),
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.IconSm),
                            colorFilter = ColorFilter.tint(playtimeContentColor)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (h < 0.05) "0h" else String.format(Locale.ROOT, "%.1fh", h),
                            fontSize = if (showPlaytimeBackground) {
                                DesignTokens.TextCaption.sp
                            } else {
                                DesignTokens.TextBody2.sp
                            },
                            fontWeight = FontWeight.Bold,
                            color = playtimeTextColor
                        )
                    }
                    // 近期时长（徽章下方）
                    if (game.playtime_2weeks != null && game.playtime_2weeks > 0) {
                        Text(
                            text = "+${String.format(Locale.ROOT, "%.1f", game.playtime_2weeks / 60.0)}h",
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
            val reviewText by viewModel.reviewScore(context, game.appid)
                .collectAsStateWithLifecycle()
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReviewScore(reviewText)
                Spacer(Modifier.weight(1f))
                if (markRes in GameMarks.markResIds) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(GameMarks.colorFor(markRes)), CircleShape)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(markRes),
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val rawList = uiState.games
    val loading = uiState.isLoading

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    val specialsError = uiState.error
    val specialsErrorMessage = specialsError?.let { (resId, arg) ->
        if (arg != null) stringResource(resId, arg) else stringResource(resId)
    }
    LaunchedEffect(specialsError, specialsErrorMessage) {
        specialsErrorMessage?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
        if (specialsError != null) viewModel.clearError()
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
    Crossfade(loading == true, animationSpec = tween(250)) { showSkeleton ->
    if (showSkeleton) {
        SpecialsSkeleton(topBarInsetDp)
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topBarInsetDp, bottom = 72.dp)
        ) {
            itemsIndexed(filteredList, key = { idx, item -> "s_${item.id}_$idx" }) { _, game ->
                MarketGameItem(game, modifier = Modifier.animateItem()) {
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
        stringResource(R.string.specials_price_all),
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
        Card(
            modifier = Modifier
                .motionDialogSurface()
                .padding(32.dp)
                .then(stopPropagation)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.specials_filter),
                    fontWeight = FontWeight.Bold,
                    fontSize = DesignTokens.TextBody1.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                // ── 排序方式 ──
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                        .motionClickable {
                            showSortOptions = !showSortOptions
                            showPriceOptions = false
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.specials_sort_title),
                        fontSize = DesignTokens.TextSubtitle.sp,
                        fontWeight = systemFontWeight(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(32.dp))
                    Text(
                        text = stringResource(sortOptions[currentSort]),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                    )
                    Spacer(Modifier.width(4.dp))
                    ExpandableArrow(
                        expanded = showSortOptions,
                        color = if (showSortOptions) {
                            DesignTokens.AccentBlue
                        } else {
                            MiuixTheme.colorScheme.onSurface.copy(
                                alpha = DesignTokens.OpacityBody
                            )
                        }
                    )
                }
                AnimatedVisibility(
                    visible = showSortOptions,
                    enter = smoothExpandEnter(),
                    exit = smoothExpandExit()
                ) {
                    Column {
                        sortOptions.forEachIndexed { i, resId ->
                            val sel = currentSort == i
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .motionClickable {
                                        onSortSelected(i)
                                        showSortOptions = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = stringResource(resId),
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
                        .motionClickable {
                            showPriceOptions = !showPriceOptions
                            showSortOptions = false
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.specials_price_filter),
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
                    ExpandableArrow(
                        expanded = showPriceOptions,
                        color = if (showPriceOptions) {
                            DesignTokens.AccentBlue
                        } else {
                            MiuixTheme.colorScheme.onSurface.copy(
                                alpha = DesignTokens.OpacityBody
                            )
                        }
                    )
                }
                AnimatedVisibility(
                    visible = showPriceOptions,
                    enter = smoothExpandEnter(),
                    exit = smoothExpandExit()
                ) {
                    Column {
                        priceOptions.forEachIndexed { i, label ->
                            val sel = currentPrice == i
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .motionClickable {
                                        onPriceSelected(i)
                                        showPriceOptions = false
                                    }
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
                        text = stringResource(R.string.specials_filter_hide_owned),
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
internal fun MarketGameItem(
    game: MarketGame,
    modifier: Modifier = Modifier,
    showPrice: Boolean = true,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .motionClickable(pressedScale = 0.985f, onClick = onClick)
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
                    text = "${game.reviewScore}% " + stringResource(R.string.detail_positive).trim(),
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
            if (showPrice && !game.originalPriceStr.isNullOrEmpty() && game.discount > 0) {
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
            if (showPrice) {
                Text(
                    text = game.finalPriceStr,
                    fontSize = DesignTokens.TextBody1.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── 动漫条目骨架（100×140 封面 + 文字行 + 状态行） ──

@Composable
private fun ActivityPage(
    listState: LazyListState,
    libraryViewModel: LibraryViewModel,
    bangumiViewModel: BangumiViewModel,
    coverMotion: State<CoverMotion>,
    includeAnime: Boolean,
    onNavigateToDetail: (Int, String, String) -> Unit,
    onNavigateToBangumiDetail: (Int, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val yearFontFamily = remember {
        FontFamily(Font(R.font.digital_numbers_regular, FontWeight.Bold))
    }
    val libraryUiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val steamLoading = libraryUiState.isLoading
    val bangumiUiState by bangumiViewModel.uiState.collectAsStateWithLifecycle()
    val bangumiLoading = bangumiUiState.isLoading
    val revision by ActivityStats.revision.collectAsStateWithLifecycle()
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val today = remember { activityDateString(Calendar.getInstance()) }
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var selectedDate by remember { mutableStateOf(today) }
    var showExactDate by remember { mutableStateOf(true) }
    var yearStats by remember { mutableStateOf<Map<String, DailyActivity>>(emptyMap()) }
    var activityHistory by remember { mutableStateOf<Map<String, DailyActivity>>(emptyMap()) }
    var availableYears by remember { mutableStateOf(setOf(currentYear)) }
    var baselineOnly by remember { mutableStateOf(false) }
    var snapshotLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val resolvedGamePortraits = remember { mutableStateMapOf<Int, String>() }
    val resolvingGamePortraits = remember { mutableStateMapOf<Int, Boolean>() }
    val failedGamePortraits = remember { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(revision, selectedYear, includeAnime) {
        snapshotLoading = true
        val snapshot = withContext(Dispatchers.IO) {
            ActivityStats.getYearSnapshot(
                context = context,
                year = selectedYear,
                includeAnime = includeAnime
            )
        }
        yearStats = snapshot.stats
        activityHistory = snapshot.history
        availableYears = snapshot.availableYears + currentYear
        baselineOnly = snapshot.baselineOnly
        if (!selectedDate.startsWith("$selectedYear-")) {
            selectedDate = if (selectedYear == currentYear) {
                today
            } else {
                yearStats.keys.maxOrNull() ?: "$selectedYear-12-31"
            }
        }
        snapshotLoading = false
    }

    val historyEntries = remember(activityHistory, selectedDate) {
        activityHistory.asSequence()
            .filter { (date, _) -> date <= selectedDate }
            .flatMap { (_, day) -> day.entries.asSequence() }
            .sortedByDescending { it.lastRecordedAt }
            .distinctBy { it.kind to it.id }
            .toList()
    }
    val displayedEntries = if (showExactDate) {
        yearStats[selectedDate]?.entries.orEmpty()
    } else {
        historyEntries
    }
    val historyTotals = remember(activityHistory, selectedDate) {
        activityHistory.asSequence()
            .filter { (date, _) -> date <= selectedDate }
            .fold(0 to 0.0) { (gameMinutes, animeEpisodes), (_, day) ->
                gameMinutes + day.gameMinutes to animeEpisodes + day.animeEpisodes
            }
    }
    val selectedDay = yearStats[selectedDate]
    val summaryGameMinutes =
        if (showExactDate) selectedDay?.gameMinutes ?: 0 else historyTotals.first
    val summaryAnimeEpisodes =
        if (showExactDate) selectedDay?.animeEpisodes ?: 0.0 else historyTotals.second
    val minYear = availableYears.minOrNull() ?: currentYear
    val apiKey = UserPrefs.getApiKey(context)
    val steamId = UserPrefs.getSteamId(context)
    val bgmUsername = UserPrefs.getBangumiUsername(context)
    val bgmAccessToken = UserPrefs.getBangumiAccessToken(context)
    val topBarInsetDp = 48.dp + statusBarHeightDp() + 4.dp
    val activityLoading =
        snapshotLoading || steamLoading == true || includeAnime && bangumiLoading == true

    Crossfade(
        targetState = activityLoading,
        animationSpec = tween(DesignTokens.AnimDuration),
        label = "activity_page_loading"
    ) { loading ->
    if (loading) {
        ActivityPageLoadingSkeleton(topBarInsetDp)
    } else {
        CompactPullToRefresh(
        isRefreshing = false,
        onRefresh = {
            libraryViewModel.refresh(apiKey, steamId, context)
            if (
                includeAnime &&
                bgmUsername.isNotBlank() &&
                bgmAccessToken.isNotBlank()
            ) {
                bangumiViewModel.refresh(bgmUsername, bgmAccessToken, context)
            }
        },
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topBarInsetDp),
    ) { refreshContentModifier ->
        LazyColumn(
            state = listState,
            modifier = refreshContentModifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    showExactDate = false
                },
            contentPadding = PaddingValues(bottom = DesignTokens.SpaceXl)
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
                        onClick = {
                            selectedYear--
                            showExactDate = false
                        },
                        enabled = selectedYear > minYear
                    ) {
                        Image(
                            imageVector = MiuixIcons.Light.ChevronBackward,
                            contentDescription = stringResource(R.string.activity_previous_year),
                            modifier = Modifier.size(DesignTokens.IconLg),
                            colorFilter = ColorFilter.tint(
                                MiuixTheme.colorScheme.onSurface.copy(
                                    alpha = if (selectedYear > minYear) 1f else DesignTokens.OpacityInactive
                                )
                            )
                        )
                    }
                    AnimatedContent(
                        targetState = selectedYear,
                        transitionSpec = {
                            (fadeIn(tween(DesignTokens.FadeInDuration)) +
                                slideInVertically { height -> height / 3 })
                                .togetherWith(
                                    fadeOut(tween(DesignTokens.FadeOutDuration)) +
                                        slideOutVertically { height -> -height / 3 }
                                )
                        },
                        label = "activity_year_change"
                    ) { year ->
                        Text(
                            text = year.toString(),
                            modifier = Modifier.padding(horizontal = 24.dp),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = yearFontFamily,
                            color = DesignTokens.AccentBlue
                        )
                    }
                    IconButton(
                        onClick = {
                            selectedYear++
                            showExactDate = false
                        },
                        enabled = selectedYear < currentYear
                    ) {
                        Image(
                            imageVector = MiuixIcons.Light.ChevronForward,
                            contentDescription = stringResource(R.string.activity_next_year),
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
                    selectedDate = selectedDate.takeIf { showExactDate },
                    stats = yearStats,
                    onDateSelected = {
                        selectedDate = it
                        showExactDate = true
                    }
                )
            }
            item("activity_summary") {
                val summaryState = ActivitySummaryUi(
                    date = selectedDate.takeIf { showExactDate },
                    gameMinutes = summaryGameMinutes,
                    animeEpisodes = summaryAnimeEpisodes,
                    includeAnime = includeAnime
                )
                AnimatedContent(
                    targetState = summaryState,
                    transitionSpec = {
                        (fadeIn(tween(DesignTokens.FadeInDuration)) +
                            slideInVertically { height -> height / 4 })
                            .togetherWith(
                                fadeOut(tween(DesignTokens.FadeOutDuration)) +
                                    slideOutVertically { height -> -height / 4 }
                            )
                    },
                    label = "activity_summary_change"
                ) { summary ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (summary.date != null) {
                            Text(
                                text = summary.date,
                                fontSize = DesignTokens.TextBody1.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = yearFontFamily
                            )
                            Spacer(Modifier.height(DesignTokens.SpaceSm))
                        }
                        Text(
                            text = if (summary.includeAnime) {
                                stringResource(
                                    R.string.activity_stats_combined,
                                    formatActivityHours(summary.gameMinutes),
                                    formatEpisodeAmount(summary.animeEpisodes)
                                )
                            } else {
                                stringResource(
                                    R.string.activity_stats_game_only,
                                    formatActivityHours(summary.gameMinutes)
                                )
                            },
                            fontSize = DesignTokens.TextBody2.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(
                                alpha = DesignTokens.OpacityBody
                            )
                        )
                    }
                }
            }

            if (displayedEntries.isEmpty()) {
                item("activity_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(
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
                val rows = displayedEntries.chunked(3)
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
                            .animateItem()
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rows[rowIndex].forEach { entry ->
                            ActivityCover(
                                entry = entry,
                                modifier = Modifier.weight(1f),
                                coverMotion = coverMotion,
                                resolvedPortraitUrl = resolvedGamePortraits[entry.id],
                                useFallback = failedGamePortraits[entry.id] == true,
                                onPortraitError = {
                                    if (resolvedGamePortraits.containsKey(entry.id)) {
                                        failedGamePortraits[entry.id] = true
                                    } else if (resolvingGamePortraits[entry.id] != true) {
                                        resolvingGamePortraits[entry.id] = true
                                        coroutineScope.launch {
                                            val portraitUrl = withContext(Dispatchers.IO) {
                                                runCatchingCancellable {
                                                    resolveSteamPortraitUrl(entry.id)
                                                }.getOrNull()
                                            }
                                            resolvingGamePortraits.remove(entry.id)
                                            if (portraitUrl != null) {
                                                cachePortraitUrl(context, entry.id, portraitUrl)
                                                resolvedGamePortraits[entry.id] = portraitUrl
                                                failedGamePortraits.remove(entry.id)
                                            } else {
                                                failedGamePortraits[entry.id] = true
                                            }
                                        }
                                    }
                                },
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
                        repeat(3 - rows[rowIndex].size) { Spacer(Modifier.weight(1f)) }
                    }
                }
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
    selectedDate: String?,
    stats: Map<String, DailyActivity>,
    onDateSelected: (String) -> Unit
) {
    val dates = remember(year) { activityDatesForYear(year) }
    val rows = remember(dates) { (dates.size + 24) / 25 }
    val heatmapDescription = stringResource(R.string.activity_heatmap_description, year)
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
                    heatmapDescription
            }
            .pointerInput(dates, today, canvasSize) {
                detectTapGestures { offset ->
                    if (canvasSize.width <= 0 || canvasSize.height <= 0) return@detectTapGestures
                    val column = (offset.x / (canvasSize.width / 25f)).toInt()
                    val visualRow = (offset.y / (canvasSize.height / rows.toFloat())).toInt()
                    val chronologicalRow = rows - 1 - visualRow
                    val index = chronologicalRow * 25 + column
                    val date = dates.getOrNull(index) ?: return@detectTapGestures
                    if (date <= today) onDateSelected(date)
                }
            }
    ) {
        val slotWidth = size.width / 25f
        val slotHeight = size.height / rows
        val cellSize = minOf(slotWidth, slotHeight) - gapPx
        val levelSpan = 20.0 / 6.0
        dates.forEachIndexed { index, date ->
            val column = index % 25
            val row = rows - 1 - index / 25
            val score = stats[date]?.score ?: 0.0
            val cellColor = when {
                score <= 0.0 -> emptyColor
                score <= levelSpan -> DesignTokens.AccentBlue.copy(alpha = 0.25f)
                score <= levelSpan * 2 -> DesignTokens.AccentBlue.copy(alpha = 0.40f)
                score <= levelSpan * 3 -> DesignTokens.AccentBlue.copy(alpha = 0.55f)
                score <= levelSpan * 4 -> DesignTokens.AccentBlue.copy(alpha = 0.70f)
                score <= levelSpan * 5 -> DesignTokens.AccentBlue.copy(alpha = 0.85f)
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
private fun ActivityCover(
    entry: ActivityEntry,
    modifier: Modifier,
    coverMotion: State<CoverMotion>,
    resolvedPortraitUrl: String?,
    useFallback: Boolean,
    onPortraitError: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cachedPortraitUrl = remember(entry.kind, entry.id) {
        if (entry.kind == ActivityKind.GAME) {
            readCachedPortraitUrl(context, entry.id)
        } else {
            null
        }
    }
    val imageModel = remember(
        entry.kind,
        entry.id,
        entry.imageUrl,
        resolvedPortraitUrl,
        cachedPortraitUrl,
        useFallback
    ) {
        if (entry.kind == ActivityKind.GAME && useFallback) {
            buildHeaderUrl(context, entry.id)
        } else {
            resolvedPortraitUrl ?: cachedPortraitUrl ?: entry.imageUrl
        }
    }
    val description = stringResource(
        if (entry.kind == ActivityKind.GAME) {
            R.string.activity_game_cover
        } else {
            R.string.activity_anime_cover
        },
        entry.secondaryTitle.ifBlank { entry.title }
    )
    val coverShape = RoundedCornerShape(DesignTokens.CornerMedium)
    Box(modifier = modifier.aspectRatio(PORTRAIT_COVER_ASPECT_RATIO)) {
        Box(
            Modifier
                .matchParentSize()
                .metallicCoverShadow(coverMotion, coverShape)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .metallicCoverTilt(coverMotion, coverShape)
                .clip(coverShape)
                .background(MiuixTheme.colorScheme.surfaceVariant)
                .motionClickable(pressedScale = 0.95f, onClick = onClick)
        ) {
        val imageRequest = remember(imageModel, entry.kind, entry.id) {
            ImageRequest.Builder(context)
                .data(imageModel)
                .crossfade(true)
                .listener(
                    onError = { _, _ ->
                        if (entry.kind == ActivityKind.GAME && !useFallback) {
                            onPortraitError()
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
            MetallicCoverOverlay(
                motion = coverMotion,
                modifier = Modifier.fillMaxSize()
            )
        }
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

private fun formatActivityHours(minutes: Int): String =
    String.format(Locale.US, "%.1f", minutes / 60.0)

private data class ActivitySummaryUi(
    val date: String?,
    val gameMinutes: Int,
    val animeEpisodes: Double,
    val includeAnime: Boolean
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
    coverMotion: State<CoverMotion>,
    typeFilter: Int,
    onTypeFilterChange: (Int) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateToDetail: (Int, String, String, String) -> Unit,
    onNavigateToDetailWithCover: (Int, String, String, String, Rect, ImageBitmap) -> Unit,
    profileBackgroundFile: File?,
    viewModel: BangumiViewModel
) {
    val context = LocalContext.current
    val displayStyle = ThemeUtils.getDisplayStyle(context)  // 0=list, 1=grid
    val ratingMode = UserPrefs.getBangumiRatingMode(context)
    val bgmUsername = UserPrefs.getBangumiUsername(context)
    val bgmAccessToken = UserPrefs.getBangumiAccessToken(context)
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loading = uiState.isLoading
    val refreshing = uiState.isRefreshing
    val error = uiState.error
    val collections = uiState.collections
    val bgmUser = uiState.user
    val ratingsMap = uiState.ratings
    val episodeTotalsMap = uiState.episodeTotals
    val watchedEpisodeCountsMap = uiState.watchedEpisodeCounts
    val bangumiErrorMessage = error?.let { (resId, arg) ->
        if (arg == null) stringResource(resId) else stringResource(resId, arg)
    }

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
                viewModel.applyCachedCollectionChange(
                    bgmUsername,
                    bgmAccessToken,
                    context
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 错误提示
    LaunchedEffect(error, bangumiErrorMessage) {
        bangumiErrorMessage?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
        if (error != null) viewModel.clearError()
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
                    text = stringResource(R.string.bangumi_no_username),
                    fontSize = DesignTokens.TextBody1.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                )
            }
        }
        return
    }

    val collectionMap = collections
    val showSkeleton = loading == true
    val showEmpty = !showSkeleton && (collectionMap == null || collectionMap.isEmpty())
    val totalCount = collectionMap?.values?.sumOf { it.size } ?: 0
    val shimmer = rememberLoadingSkeletonBrush()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val windowWidth = with(density) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val profileBackgroundAspectRatio = remember(profileBackgroundFile) {
        profileBackgroundFile?.let(BangumiProfileBackground::aspectRatio)
    }
    val profileContentHeight = 88.2.dp
    val profileOverlapHeight = DesignTokens.CornerXLarge
    val minimumProfileBackgroundHeight =
        topBarInsetDp + profileContentHeight + profileOverlapHeight
    val imageHeight = profileBackgroundAspectRatio
        ?.takeIf { it > 0f }
        ?.let { windowWidth / it * 1.2f }
    val profileBackgroundHeight = maxOf(
        minimumProfileBackgroundHeight,
        imageHeight ?: minimumProfileBackgroundHeight
    )
    val profileHeaderHeight = profileBackgroundHeight - profileOverlapHeight
    val profileAreaHeight = profileHeaderHeight - topBarInsetDp
    val profileContentGap = DesignTokens.SpaceMd
    val contentSheetShape = RoundedCornerShape(
        topStart = DesignTokens.CornerXLarge,
        topEnd = DesignTokens.CornerXLarge
    )
    val topBarInsetPx = with(density) { topBarInsetDp.toPx() }
    val profileHeaderHeightPx = with(density) { profileHeaderHeight.toPx() }
    val profileBackgroundModel = remember(profileBackgroundFile) {
        profileBackgroundFile?.let { file ->
            ImageRequest.Builder(context)
                .data(file)
                .memoryCacheKey("${file.absolutePath}:${file.lastModified()}")
                .build()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scrollOffset = when (listState.firstVisibleItemIndex) {
                        0 -> listState.firstVisibleItemScrollOffset.toFloat()
                        1 -> topBarInsetPx + listState.firstVisibleItemScrollOffset
                        else -> profileHeaderHeightPx
                    }
                    translationY = -scrollOffset
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(profileBackgroundHeight)
                    .background(MiuixTheme.colorScheme.secondaryContainer)
            ) {
                if (!showSkeleton && profileBackgroundModel != null) {
                    AsyncImage(
                        model = profileBackgroundModel,
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .profileGravityBackground(coverMotion),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(DesignTokens.ScrimDark.copy(alpha = 0.15f))
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(profileAreaHeight)
                        .offset(y = topBarInsetDp)
                        .padding(bottom = DesignTokens.SpaceXxs),
                    contentAlignment = Alignment.BottomStart
                ) {
                    if (showSkeleton) {
                        BangumiProfileSkeleton(shimmer)
                    } else {
                        BangumiProfileCard(
                            username = bgmUsername,
                            user = bgmUser,
                            totalCount = totalCount,
                            hasCustomBackground = profileBackgroundFile != null,
                            motion = coverMotion
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = profileHeaderHeight)
                    .clip(contentSheetShape)
                    .background(MiuixTheme.colorScheme.background)
            )
        }

    CompactPullToRefresh(
        isRefreshing = refreshing == true,
        onRefresh = { viewModel.refresh(bgmUsername, bgmAccessToken, context) },
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = profileHeaderHeight + profileContentGap),
    ) { refreshContentModifier ->
        // ── 骨架屏数量 ──
        val windowHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height.toDp()
        }
        val skeletonCount = remember(windowHeight) {
            maxOf(4, ((windowHeight - 260.dp) / 132.dp).toInt())
        }
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
        LazyColumn(
            state = listState,
            modifier = refreshContentModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            item("top_spacer") { Spacer(Modifier.height(topBarInsetDp)) }
            item("bgm_profile_space") { Spacer(Modifier.height(profileAreaHeight)) }
            item("bgm_profile_content_gap") {
                Spacer(Modifier.height(profileContentGap))
            }

            if (showSkeleton) {
                // ── 加载骨架 ──
                // 搜索栏骨架
                item("skel_search") {
                    Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 18.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                        .background(loadingSkeletonBaseColor()))
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
                            text = stringResource(R.string.bangumi_empty),
                            fontSize = DesignTokens.TextBody1.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                        )
                    }
                }
            } else {
                // ── 搜索栏 ──
                item("search_bar") {
                    var textFieldValue by remember(searchQuery) { mutableStateOf(searchQuery) }
                    TextField(
                        value = textFieldValue,
                        onValueChange = { v -> textFieldValue = v; onSearchQueryChange(v.trim()) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                        label = stringResource(R.string.bangumi_search),
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
                                label = stringResource(nameRes),
                                selected = typeFilter == type,
                                color = BANGUMI_TYPE_COLORS[type],
                                count = collectionMap?.get(type)?.size ?: 0,
                                onClick = {
                                    onTypeFilterChange(if (typeFilter == type) 2 else type)
                                }
                            )
                        }
                    }
                    }
                }

                // ── 动漫列表 ──
                if (displayStyle == 1) {
                    // 网格模式：每行3个封面
                    val chunked = filteredItems.chunked(3)
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
                            modifier = Modifier.fillMaxWidth().padding(
                                horizontal = BANGUMI_GRID_HORIZONTAL_PADDING,
                                vertical = DesignTokens.SpaceXs
                            ),
                            horizontalArrangement = Arrangement.spacedBy(BANGUMI_GRID_ITEM_SPACING)
                        ) {
                            chunked[rowIndex].forEach { (item, _) ->
                                val sub = item.subject
                                val gridImageUrl = sub?.images?.common ?: sub?.images?.medium
                                BangumiGridItem(
                                    imageUrl = gridImageUrl,
                                    modifier = Modifier.weight(1f),
                                    coverMotion = coverMotion,
                                    onClick = { sourceRect, imageBitmap ->
                                        val subjectName = sub?.name ?: ""
                                        val subjectNameCn = sub?.name_cn ?: ""
                                        val subjectImage = gridImageUrl ?: sub?.images?.large ?: ""
                                        if (imageBitmap != null) {
                                            onNavigateToDetailWithCover(
                                                item.subject_id,
                                                subjectName,
                                                subjectNameCn,
                                                subjectImage,
                                                sourceRect,
                                                imageBitmap
                                            )
                                        } else {
                                            onNavigateToDetail(
                                                item.subject_id,
                                                subjectName,
                                                subjectNameCn,
                                                subjectImage
                                            )
                                        }
                                    }
                                )
                            }
                            // 填充空位
                            repeat(3 - chunked[rowIndex].size) {
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
                            modifier = Modifier.animateItem(),
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
}

@Composable
internal fun BangumiItem(
    item: BangumiCollection,
    type: Int,
    ratingMode: Int,
    ratings: Map<Int, Double>,
    episodeTotals: Map<Int, Int>,
    watchedEpisodeCounts: Map<Int, Int>,
    modifier: Modifier = Modifier,
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
            stringResource(R.string.bangumi_episode_total, episodeTotal)
        type != 1 && watchedEpisodeCount > 0 ->
            if (episodeTotal != null) {
                stringResource(
                    R.string.bangumi_card_episode_progress,
                    watchedEpisodeCount,
                    episodeTotal
                )
            } else {
                stringResource(R.string.bangumi_card_episode_seen, watchedEpisodeCount)
            }
        type != 1 && episodeTotal != null ->
            stringResource(R.string.bangumi_card_episode_count, episodeTotal)
        else -> null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .motionClickable(pressedScale = 0.985f, onClick = onClick)
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
        val myRate = if (item.rate > 0) item.rate.toDouble() else null
        val globalScore = ratings[item.subject_id] ?: normalizeBangumiScore(sub.rating)
        val score: Double? = when (ratingMode) {
            1 -> myRate          // 仅我的评分
            2 -> null            // 不展示
            else -> globalScore  // 展示评分（默认）
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.height(coverH).padding(vertical = 2.dp), horizontalAlignment = Alignment.End) {
            if (score != null && score > 0) {
                val scoreText = if (ratingMode == 1) "${item.rate}" else String.format(Locale.ROOT, "%.1f", score)
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
private fun BangumiGridItem(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    coverMotion: State<CoverMotion>,
    onClick: (Rect, ImageBitmap?) -> Unit
) {
    val coverShape = RoundedCornerShape(BANGUMI_GRID_COVER_CORNER)
    val coverPainter = rememberAsyncImagePainter(model = imageUrl)
    var coverRect by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier = modifier
            .aspectRatio(PORTRAIT_COVER_ASPECT_RATIO)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                coverRect = Rect(
                    offset = position,
                    size = Size(
                        width = coordinates.size.width.toFloat(),
                        height = coordinates.size.height.toFloat()
                    )
                )
            }
    ) {
        Box(
            Modifier
                .matchParentSize()
                .metallicCoverShadow(coverMotion, coverShape)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .metallicCoverTilt(coverMotion, coverShape)
                .clip(coverShape)
                .background(MiuixTheme.colorScheme.surfaceVariant)
                .motionClickable(
                    enabled = true,
                    pressedScale = 1f,
                    pressedAlpha = 1f,
                    onClick = {
                        if (coverRect.width > 0f && coverRect.height > 0f) {
                            val imageBitmap = (
                                coverPainter.state.value as? AsyncImagePainter.State.Success
                                )?.result?.image
                                ?.let { it as? BitmapImage }
                                ?.bitmap
                                ?.takeUnless { it.isRecycled }
                                ?.asImageBitmap()
                            onClick(coverRect, imageBitmap)
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                Image(
                    painter = coverPainter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            MetallicCoverOverlay(
                motion = coverMotion,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ── 动漫个人资料卡 ──
@Composable
private fun BangumiProfileSkeleton(shimmer: androidx.compose.ui.graphics.Brush) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 5.4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 7.2.dp,
                    bottom = 7.2.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(78.dp).clip(CircleShape).background(shimmer))
                Spacer(Modifier.width(24.dp))
                Column(Modifier.width(140.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.45f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(DesignTokens.CornerSmall))
                            .background(shimmer)
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.25f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(DesignTokens.CornerSmall))
                            .background(shimmer)
                    )
                }
            }
        }
    }
}

@Composable
private fun BangumiProfileCard(
    username: String,
    user: BangumiUser?,
    totalCount: Int,
    hasCustomBackground: Boolean,
    motion: State<CoverMotion>
) {
    // Bangumi 用户头像（medium 尺寸，对齐游戏页资料头像）
    val avatarUrl = user?.avatar?.medium ?: user?.avatar?.small
    val displayName = user?.nickname?.ifBlank { null } ?: "@$username"
    val primaryTextColor = if (hasCustomBackground) {
        Color.White
    } else {
        MiuixTheme.colorScheme.onSurface
    }
    val secondaryTextColor = primaryTextColor.copy(alpha = DesignTokens.OpacityBody)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 5.4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 7.2.dp,
                    bottom = 7.2.dp
                )
        ) {
            // 头像 + 用户名行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .profileGravityForeground(motion),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像（对齐游戏页资料头像）
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(7.2.dp))
                        .background(
                            if (hasCustomBackground) {
                                Color.White.copy(alpha = 0.16f)
                            } else {
                                MiuixTheme.colorScheme.surfaceVariant
                            }
                        ),
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
                            modifier = Modifier.size(25.2.dp),
                            colorFilter = ColorFilter.tint(secondaryTextColor)
                        )
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column {
                    Text(
                        text = displayName,
                        color = primaryTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = DesignTokens.TextHeadline.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.bangumi_profile_total, totalCount),
                        color = secondaryTextColor,
                        fontSize = DesignTokens.TextBody2.sp
                    )
                }
            }
        }
    }
}
