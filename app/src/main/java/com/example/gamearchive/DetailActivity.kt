package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import androidx.compose.ui.state.ToggleableState

class DetailActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)

        val appId = intent.getIntExtra("APP_ID", 0)
        val appName = intent.getStringExtra("APP_NAME") ?: "Unknown"
        val price = intent.getStringExtra("APP_PRICE") ?: ""

        val colorSchemeMode = when (ThemeUtils.getThemeMode(this)) {
            0 -> ColorSchemeMode.Light
            1 -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }

        setContent {
            MiuixTheme(controller = ThemeController(colorSchemeMode = colorSchemeMode)) {
                DetailScreen(appId = appId, appName = appName, price = price, onBack = { finish() })
            }
        }
    }
}

// ── 颜色 → DesignTokens

// ── 媒体条目 ──
private data class MediaItem(val url: String, val thumbnailUrl: String, val isVideo: Boolean)

@Composable
private fun DetailScreen(appId: Int, appName: String, price: String, onBack: () -> Unit) {
    val context = LocalContext.current

    // 数据状态
    var gameName by remember { mutableStateOf(appName) }
    var finalPrice by remember { mutableStateOf(price) }
    var originalPrice by remember { mutableStateOf("") }
    var discountPercent by remember { mutableIntStateOf(0) }
    var releaseDate by remember { mutableStateOf("") }
    var developer by remember { mutableStateOf("") }
    var descriptionHtml by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // 标记状态
    var currentMark by remember { mutableIntStateOf(GameMarks.getMark(context, appId)) }
    var showMarkSheet by remember { mutableStateOf(false) }

    // 标签状态
    var gameTags by remember { mutableStateOf(GameTags.getTagsForGame(context, appId)) }
    var showTagSheet by remember { mutableStateOf(false) }
    val allTags = remember { GameTags.getAllTags(context) }

    // 评价状态
    var reviewSummary by remember { mutableStateOf("") }
    var reviewPercent by remember { mutableIntStateOf(-1) }
    var reviewCount by remember { mutableIntStateOf(0) }
    var reviews by remember { mutableStateOf<List<SteamReview>>(emptyList()) }

    val mediaList = remember { mutableStateListOf<MediaItem>() }

    // 状态栏高度
    val statusBarDp = statusBarHeightDp()

    // 屏幕宽度（用于媒体画廊高度计算）
    val screenWidth = context.resources.displayMetrics.widthPixels
    val mediaHeight = with(LocalDensity.current) { (screenWidth * 9 / 16).toDp() }

    // 加载数据
    LaunchedEffect(appId) {
        if (appId == 0) { isLoading = false; return@LaunchedEffect }
        try {
            try {
                val api = GameArchiveApp.apiService
            val l = LocaleHelper.getApiLanguage(context)

            val (detailsResp, reviewsResp) = coroutineScope {
                val d = async { try { api.getGameDetails(appId, l = l) } catch (_: Exception) { null } }
                val r = async { try { api.getGameReviews(appId, l = l, count = 50) } catch (_: Exception) { null } }
                Pair(d.await(), r.await())
            }

            val data = detailsResp?.get(appId.toString())?.data
            if (data != null) {
                if (!data.name.isNullOrEmpty()) gameName = data.name

                // 媒体
                data.movies?.take(3)?.forEach { movie ->
                    val videoUrl = movie.mp4?.max ?: movie.mp4?.p480
                        ?: movie.webm?.max ?: movie.webm?.p480
                        ?: if (movie.id != null) "https://cdn.cloudflare.steamstatic.com/steam/apps/${movie.id}/movie_max.mp4" else null
                    val thumb = movie.thumbnail
                    if (videoUrl != null && thumb != null)
                        mediaList.add(MediaItem(videoUrl, thumb, true))
                }
                data.screenshots?.take(10)?.forEach { shot ->
                    val full = shot.path_full
                    val thumb = shot.path_thumbnail
                    if (full != null && thumb != null)
                        mediaList.add(MediaItem(full, thumb, false))
                }

                // 开发商 / 日期
                releaseDate = data.release_date?.date ?: context.getString(R.string.detail_unknown)
                developer = data.developers?.firstOrNull() ?: context.getString(R.string.detail_unknown)

                // 价格
                val pi = data.price_overview
                if (pi != null) {
                    finalPrice = pi.final_formatted ?: finalPrice
                    if ((pi.discount_percent ?: 0) > 0) {
                        discountPercent = pi.discount_percent!!
                        originalPrice = pi.initial_formatted ?: ""
                    }
                }

                // 描述 HTML
                var rawDesc = data.detailed_description ?: data.short_description ?: ""
                rawDesc = rawDesc.replace(Regex("(?i)<p[^>]*>\\s*&nbsp;\\s*</p>"), "")
                rawDesc = rawDesc.replace(Regex("(?i)<p[^>]*>\\s*</p>"), "")

                val isNight = (context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

                val bodyColor = String.format("#%06X", (if (isNight) 0xFFB0B0B5.toInt() else 0xFF6A6A6F.toInt()) and 0xFFFFFF)
                val headColor = String.format("#%06X", (if (isNight) 0xFFF2F2F2.toInt() else 0xFF000000.toInt()) and 0xFFFFFF)
                val linkColor = String.format("#%06X", (if (isNight) 0xFF5E9BFF.toInt() else 0xFF3482FF.toInt()) and 0xFFFFFF)

                val css = """
                    <style>
                        * { margin:0;padding:0;box-sizing:border-box;max-width:100%; }
                        body { background:transparent;color:$bodyColor;font-family:sans-serif;width:100vw;overflow-x:hidden;font-size:15px;line-height:1.6; }
                        img,video { display:block!important;max-width:100%!important;width:auto!important;height:auto!important;border:0!important;margin:0!important; }
                        a { color:$linkColor;text-decoration:none;font-weight:bold; }
                        h1,h2,h3 { margin:24px 0 12px 0!important;font-weight:bold;line-height:1.4!important;color:$headColor;font-size:18px!important; }
                        p { margin-bottom:12px!important; }
                        ul,ol { margin-left:20px!important;margin-bottom:12px!important; }
                    </style>
                """.trimIndent()

                val jsScript = """
                    <script>
                        document.addEventListener("DOMContentLoaded",function(){
                            var els=document.body.querySelectorAll('p,div,span,a,h1,h2,h3,li');
                            for(var i=0;i<els.length;i++){
                                var el=els[i];
                                var hasImg=el.querySelector('img')||el.querySelector('video');
                                var hasText=el.innerText.replace(/\s/g,'').length>0;
                                if(hasImg&&!hasText){el.style.margin='0';el.style.padding='0';el.style.lineHeight='0';el.style.fontSize='0';el.style.display='block';}
                                else if(hasImg){el.style.display='block';}
                            }
                            var bb=document.querySelectorAll('.bb_img_ctn');
                            for(var j=0;j<bb.length;j++){bb[j].style.display='block';bb[j].style.lineHeight='0';bb[j].style.margin='0';}
                        });
                    </script>
                """.trimIndent()

                descriptionHtml = "<html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">$css</head><body>$rawDesc $jsScript</body></html>"
            }

            // 评价
            val summary = reviewsResp?.query_summary
            if (summary != null && summary.total_reviews > 0) {
                reviewSummary = summary.review_score_desc ?: context.getString(R.string.general_no_data)
                reviewPercent = (summary.total_positive.toDouble() / summary.total_reviews.toDouble() * 100).toInt()
                reviewCount = summary.total_reviews
                reviews = reviewsResp.reviews ?: emptyList()
            } else {
                reviewSummary = context.getString(R.string.general_no_data)
            }
            } catch (e: Exception) {
                android.util.Log.e("DetailActivity", "Failed to load game details for appId=$appId", e)
            }
        } finally {
            isLoading = false
        }
    }

    // ── UI ──
    val scrollState = rememberScrollState()

    // 顶栏滑动显隐
    var topBarVisible by remember { mutableStateOf(true) }
    val lastScrollY = remember { mutableStateOf(0f) }
    LaunchedEffect(scrollState.value) {
        val currentY = scrollState.value.toFloat()
        if (currentY > lastScrollY.value + 20f && currentY > 100f) {
            topBarVisible = false
        } else if (currentY < lastScrollY.value - 20f) {
            topBarVisible = true
        }
        lastScrollY.value = currentY
    }

    val topBarHeightDp = 52.dp + statusBarDp + 4.dp
    val density = LocalDensity.current
    val topBarOffsetY by animateDpAsState(
        targetValue = if (topBarVisible) 0.dp else -topBarHeightDp,
        animationSpec = tween(durationMillis = DesignTokens.AnimDuration, easing = FastOutSlowInEasing)
    )

    Surface(modifier = Modifier.fillMaxSize()) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                InfiniteProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(top = topBarHeightDp + 4.dp, bottom = 48.dp)
            ) {
                // ── 媒体画廊 ──
                if (mediaList.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { mediaList.size })
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(mediaHeight)
                            .padding(top = 8.dp)
                    ) { page ->
                        val item = mediaList[page]
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                                .background(Color.Black)
                                .clickable {
                                    val intent = Intent(context, MediaViewerActivity::class.java)
                                    intent.putStringArrayListExtra("URLS", ArrayList(mediaList.map { it.url }))
                                    intent.putStringArrayListExtra("TYPES", ArrayList(mediaList.map { if (it.isVideo) "video" else "image" }))
                                    intent.putExtra("INDEX", page)
                                    context.startActivity(intent)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(item.thumbnailUrl).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (item.isVideo) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                                Image(
                                    painter = painterResource(android.R.drawable.ic_media_play),
                                    contentDescription = "Play",
                                    modifier = Modifier.size(DesignTokens.IconPlay),
                                    colorFilter = ColorFilter.tint(Color.White)
                                )
                            }
                        }
                    }
                }

                // ── 价格 + 评价 —— 单卡片竖线分割 ──
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    cornerRadius = DesignTokens.CornerLarge
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 价格区
                        Row(
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (discountPercent > 0) {
                                Box(
                                    modifier = Modifier
                                        .background(DesignTokens.DiscountGreen, RoundedCornerShape(DesignTokens.CornerMedium))
                                        .padding(horizontal = 6.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "-$discountPercent%",
                                        fontWeight = FontWeight.Black,
                                        fontSize = DesignTokens.TextBody1.sp,
                                        color = DesignTokens.DiscountGreenText
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            if (originalPrice.isNotEmpty()) {
                                Text(
                                    text = originalPrice,
                                    fontSize = DesignTokens.TextCaption.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                                    textDecoration = TextDecoration.LineThrough
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = finalPrice,
                                fontWeight = FontWeight.Bold,
                                fontSize = DesignTokens.TextSubtitle.sp,
                                maxLines = 1
                            )
                        }

                        // 竖线
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(MiuixTheme.colorScheme.outline.copy(alpha = DesignTokens.OpacityDisabled))
                        )

                        // 评价区
                        Row(
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = reviewSummary.ifEmpty { context.getString(R.string.detail_loading_review) },
                                fontWeight = FontWeight.Bold,
                                fontSize = DesignTokens.TextBody2.sp,
                                color = if (reviewPercent >= 0) reviewColor(reviewPercent) else DesignTokens.ReviewDefault,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (reviewPercent >= 0) {
                                Text(
                                    text = "$reviewPercent%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = DesignTokens.TextBody2.sp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }

                // ── 开发商 + 日期 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                ) {
                    // 开发商
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.ic_business),
                                contentDescription = null,
                                modifier = Modifier.size(DesignTokens.IconMd),
                                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody))
                            )
                            Text(
                                text = context.getString(R.string.detail_developer),
                                fontSize = DesignTokens.TextCaption.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        Text(
                            text = developer,
                            fontWeight = FontWeight.Bold,
                            fontSize = DesignTokens.TextBody1.sp,
                            color = DesignTokens.AccentBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    // 日期
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = context.getString(R.string.detail_release_date),
                                fontSize = DesignTokens.TextCaption.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Image(
                                painter = painterResource(R.drawable.ic_calendar),
                                contentDescription = null,
                                modifier = Modifier.size(DesignTokens.IconMd),
                                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody))
                            )
                        }
                        Text(
                            text = releaseDate,
                            fontSize = DesignTokens.TextBody1.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // 分割线
                // ── 游玩标记选择器 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMarkSheet = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (currentMark in GameMarks.markResIds) {
                                context.getString(currentMark)
                            } else {
                                context.getString(R.string.mark_select_status)
                            },
                            fontSize = DesignTokens.TextBody1.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentMark in GameMarks.markResIds) {
                                Color(GameMarks.statusColorMap[currentMark]!!)
                            } else {
                                MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                            }
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.ic_arrow_right),
                        contentDescription = null,
                        modifier = Modifier.size(DesignTokens.IconLg),
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityHint))
                    )
                }

                // ── 标签选择器 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTagSheet = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (gameTags.isEmpty()) {
                        Text(
                            text = context.getString(R.string.tag_edit),
                            fontSize = DesignTokens.TextBody2.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityInactive),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            gameTags.forEach { tag ->
                                Text(
                                    text = tag,
                                    fontSize = DesignTokens.TextBody1.sp,
                                    color = Color.White,
                                    modifier = Modifier
                                        .background(tagBgColor(), RoundedCornerShape(DesignTokens.CornerSmall))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "+",
                        fontSize = DesignTokens.TextHeadline.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityHint),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(Modifier.height(DesignTokens.SpaceMassive))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(DesignTokens.DividerHeight)
                        .background(MiuixTheme.colorScheme.outline)
                )

                // ── 简介标题 ──
                Text(
                    text = context.getString(R.string.detail_about),
                    fontWeight = FontWeight.Bold,
                    fontSize = DesignTokens.TextBody2.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )

                // ── 简介 WebView ──
                if (descriptionHtml.isNotEmpty()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                setBackgroundColor(0)
                                isVerticalScrollBarEnabled = false
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                                    mediaPlaybackRequiresUserGesture = false
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        // 注入 JS 后请求重新布局
                                        view?.evaluateJavascript("document.body.scrollHeight") { }
                                    }
                                }
                                loadDataWithBaseURL(null, descriptionHtml, "text/html", "utf-8", null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .defaultMinSize(minHeight = 200.dp)
                    )
                }

                // ── 评价标题 ──
                if (reviews.isNotEmpty()) {
                    Text(
                        text = context.getString(R.string.detail_reviews_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = DesignTokens.TextBody2.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 32.dp, bottom = 12.dp)
                    )

                    // ── 评价列表 ──
                    reviews.forEach { review ->
                        ReviewItem(review = review)
                    }
                }

                // 底部间距
                Spacer(Modifier.height(32.dp))
            }
        }

        // ── 顶栏叠加层（滑动自动显隐） ──
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
                    .padding(start = 4.dp, end = 8.dp)
                    .padding(top = statusBarDp + 4.dp)
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Image(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = "Back",
                        modifier = Modifier.size(DesignTokens.IconXl),
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                    )
                }
                Text(
                    text = gameName,
                    fontWeight = FontWeight.Bold,
                    fontSize = DesignTokens.TextTitle.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 48.dp)
                )
            }
        }
    } // close Box

        // ── 标记选择底部弹窗 ──
        if (showMarkSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DesignTokens.ScrimDark)
                    .clickable { showMarkSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false, onClick = {})  // 阻止事件穿透
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = context.getString(R.string.mark_select_status),
                            fontWeight = FontWeight.Bold,
                            fontSize = DesignTokens.TextSubtitle.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        // 清除标记
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    GameMarks.setMark(context, appId, -1)
                                    currentMark = -1
                                    showMarkSheet = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(DesignTokens.IconSm)
                                    .background(
                                        if (currentMark == -1) DesignTokens.AccentBlue else Color.Transparent,
                                        RoundedCornerShape(DesignTokens.CornerMedium)
                                    )
                                    .then(
                                        if (currentMark != -1) Modifier.border(
                                            1.5.dp,
                                            MiuixTheme.colorScheme.outline,
                                            RoundedCornerShape(DesignTokens.CornerMedium)
                                        )
                                        else Modifier
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.mark_clear),
                                fontSize = DesignTokens.TextBody1.sp,
                                color = if (currentMark == -1) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        // 7 种状态
                        GameMarks.markResIds.forEach { resId ->
                            val markColor = Color(GameMarks.statusColorMap[resId]!!)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        GameMarks.setMark(context, appId, resId)
                                        currentMark = resId
                                        showMarkSheet = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(DesignTokens.IconSm)
                                        .background(
                                            if (currentMark == resId) markColor else Color.Transparent,
                                            RoundedCornerShape(DesignTokens.CornerMedium)
                                        )
                                        .then(
                                            if (currentMark != resId) Modifier.border(
                                                DesignTokens.BorderThick, markColor, RoundedCornerShape(DesignTokens.CornerMedium)
                                            )
                                            else Modifier
                                        )
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = context.getString(resId),
                                    fontSize = DesignTokens.TextBody1.sp,
                                    fontWeight = if (currentMark == resId) FontWeight.Bold else FontWeight.Normal,
                                    color = markColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 标签选择底部弹窗 ──
        if (showTagSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DesignTokens.ScrimDark)
                    .clickable { showTagSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false, onClick = {})
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = context.getString(R.string.tag_select),
                            fontWeight = FontWeight.Bold,
                            fontSize = DesignTokens.TextSubtitle.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        // ── 新建标签 ──
                        var newTagInput by remember { mutableStateOf("") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            top.yukonga.miuix.kmp.basic.TextField(
                                value = newTagInput,
                                onValueChange = { newTagInput = it },
                                modifier = Modifier.weight(1f),
                                label = context.getString(R.string.tag_hint),
                                useLabelAsPlaceholder = true,
                                singleLine = true
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .height(DesignTokens.ButtonHeightSmall)
                                    .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                    .background(if (newTagInput.trim().isNotEmpty()) buttonBgColor() else buttonBgColor().copy(alpha = DesignTokens.OpacityDisabled))
                                    .then(if (newTagInput.trim().isNotEmpty()) Modifier.clickable {
                                        val trimmed = newTagInput.trim()
                                        if (trimmed.isNotEmpty() && !allTags.contains(trimmed)) {
                                            GameTags.addTag(context, trimmed)
                                            gameTags = gameTags + trimmed
                                            GameTags.setTagsForGame(context, appId, gameTags)
                                            newTagInput = ""
                                        }
                                    } else Modifier)
                                    .padding(horizontal = DesignTokens.SpaceXl, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = context.getString(R.string.tag_new), fontSize = DesignTokens.TextBody2.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        // ── 标签池 ──
                        if (allTags.isEmpty()) {
                            Text(
                                text = context.getString(R.string.general_no_data),
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityHint),
                                fontSize = DesignTokens.TextBody1.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        } else {
                            allTags.forEach { tag ->
                                val checked = gameTags.contains(tag)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        state = if (checked) ToggleableState.On else ToggleableState.Off,
                                        onClick = {
                                            gameTags = if (checked) gameTags.filter { it != tag }
                                            else gameTags + tag
                                            GameTags.setTagsForGame(context, appId, gameTags)
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(text = tag, fontSize = DesignTokens.TextBody1.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(DesignTokens.ButtonHeightSmall)
                                .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                .background(buttonBgColor())
                                .clickable { showTagSheet = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = context.getString(R.string.settings_save_profile), color = Color.White, fontWeight = FontWeight.Bold, fontSize = DesignTokens.TextBody1.sp)
                        }
                    }
                }
            }
        }
    } // close Surface
}

@Composable
private fun ReviewItem(review: SteamReview) {
    val context = LocalContext.current
    val hours = review.author.playtime_forever / 60
    val date = remember(review.timestamp_created) {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(review.timestamp_created * 1000))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        cornerRadius = DesignTokens.CornerLarge
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(
                                if (review.voted_up) R.drawable.ic_thumb_up else R.drawable.ic_thumb_down
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.IconMd),
                            colorFilter = ColorFilter.tint(
                                if (review.voted_up) DesignTokens.SuccessGreen else DesignTokens.ErrorRed
                            )
                        )
                        Text(
                            text = context.getString(
                                if (review.voted_up) R.string.review_recommended else R.string.review_not_recommended
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = DesignTokens.TextBody2.sp,
                            modifier = Modifier.padding(start = 4.dp),
                            color = if (review.voted_up) DesignTokens.SuccessGreen else DesignTokens.ErrorRed
                        )
                    }
                    Text(
                        text = context.getString(R.string.review_playtime, hours),
                        fontSize = DesignTokens.TextCaption.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                    )
                }
                Text(text = date, fontSize = DesignTokens.TextCaption.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody))
            }

            // 分割线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DesignTokens.DividerHeight)
                    .padding(vertical = 8.dp)
                    .background(MiuixTheme.colorScheme.outline)
            )

            // 内容
            Text(
                text = review.review.replace(Regex("\\[.*?\\]"), ""),
                fontSize = DesignTokens.TextBody1.sp,
                lineHeight = 20.sp
            )

            // 点赞数
            if (review.votes_up > 0) {
                Text(
                    text = context.getString(R.string.review_helpful, review.votes_up),
                    fontSize = DesignTokens.TextCaption.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

