package com.example.gamearchive

import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.icon.basic.*
import androidx.compose.ui.state.ToggleableState

private val TAG_PINYIN_INITIAL_BOUNDARIES = listOf(
    'A' to "阿", 'B' to "芭", 'C' to "擦", 'D' to "搭", 'E' to "蛾",
    'F' to "发", 'G' to "噶", 'H' to "哈", 'J' to "击", 'K' to "喀",
    'L' to "垃", 'M' to "妈", 'N' to "拿", 'O' to "哦", 'P' to "啪",
    'Q' to "期", 'R' to "然", 'S' to "撒", 'T' to "塌", 'W' to "挖",
    'X' to "昔", 'Y' to "压", 'Z' to "匝"
)

private fun tagAlphabetGroup(tag: String, collator: Collator): Char {
    val first = tag.firstOrNull() ?: return Char.MIN_VALUE
    if (first in 'a'..'z') return first.uppercaseChar()
    if (first in 'A'..'Z') return first
    if (first !in '\u4E00'..'\u9FFF') return first.uppercaseChar()
    return TAG_PINYIN_INITIAL_BOUNDARIES
        .lastOrNull { (_, boundary) -> collator.compare(first.toString(), boundary) >= 0 }
        ?.first
        ?: first
}

// ── 颜色 → DesignTokens

// ── 媒体条目 ──
private data class MediaItem(val url: String, val thumbnailUrl: String, val isVideo: Boolean)

@Composable
internal fun DetailScreen(appId: Int, appName: String, price: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val detailViewModel: DetailViewModel = viewModel()
    val detailUiState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val unknownText = stringResource(R.string.detail_unknown)
    val noDataText = stringResource(R.string.general_no_data)

    // 数据状态
    var gameName by remember { mutableStateOf(GameNames.getName(context, appId) ?: appName) }
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
    var tagRefresh by remember { mutableIntStateOf(0) }
    val tagCollator = remember {
        Collator.getInstance(Locale.SIMPLIFIED_CHINESE).apply {
            strength = Collator.PRIMARY
        }
    }
    val allTags = remember(tagRefresh) {
        GameTags.getAllTags(context)
            .distinctBy { it.lowercase() }
            .sortedWith { first, second ->
                val groupComparison = tagAlphabetGroup(first, tagCollator)
                    .compareTo(tagAlphabetGroup(second, tagCollator))
                if (groupComparison != 0) groupComparison else tagCollator.compare(first, second)
            }
    }

    // 备注状态
    var gameNote by remember { mutableStateOf(GameNotes.getNote(context, appId)) }
    var showNoteSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var noteExpanded by remember { mutableStateOf(false) }
    val playRecords by produceState<List<ItemActivityRecord>>(emptyList(), appId) {
        value = withContext(Dispatchers.IO) {
            ActivityStats.getGameRecords(context, appId)
        }
    }

    // 评价状态
    var reviewSummary by remember { mutableStateOf("") }
    var reviewPercent by remember { mutableIntStateOf(-1) }
    var reviewCount by remember { mutableIntStateOf(0) }
    var reviews by remember { mutableStateOf<List<SteamReview>>(emptyList()) }

    val mediaList = remember { mutableStateListOf<MediaItem>() }

    // 状态栏高度
    val statusBarDp = statusBarHeightDp()
    val isNightMode =
        (LocalConfiguration.current.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    // 屏幕宽度（用于媒体画廊高度计算）
    val mediaHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.width * 9 / 16).toDp()
    }

    val apiLanguage = LocaleHelper.getApiLanguage(context)
    LaunchedEffect(appId, apiLanguage) {
        detailViewModel.load(appId, apiLanguage)
    }

    // 将 ViewModel 的不可变网络结果转换为现有展示状态。
    LaunchedEffect(detailUiState, isNightMode) {
        isLoading = detailUiState.isLoading
        if (detailUiState.isLoading) return@LaunchedEffect
        try {
            try {
            val data = detailUiState.details
            val reviewsResp = detailUiState.reviews
            if (data != null) {
                if (!data.name.isNullOrEmpty() && GameNames.getName(context, appId) == null) gameName = data.name

                // 媒体
                mediaList.clear()
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
                releaseDate = data.release_date?.date ?: unknownText
                developer = data.developers?.firstOrNull() ?: unknownText

                // 价格
                val pi = data.price_overview
                if (pi != null) {
                    finalPrice = pi.final_formatted ?: finalPrice
                    pi.discount_percent?.takeIf { it > 0 }?.let { discount ->
                        discountPercent = discount
                        originalPrice = pi.initial_formatted ?: ""
                    }
                }

                // 描述 HTML
                var rawDesc = data.detailed_description ?: data.short_description ?: ""
                rawDesc = rawDesc.replace(Regex("(?i)<p[^>]*>\\s*&nbsp;\\s*</p>"), "")
                rawDesc = rawDesc.replace(Regex("(?i)<p[^>]*>\\s*</p>"), "")
                rawDesc = rawDesc.replace(
                    Regex("(?i)<img(?![^>]*crossorigin)"),
                    "<img crossorigin=\"anonymous\""
                )

                val bodyColor = String.format("#%06X", (if (isNightMode) 0xFFB0B0B5.toInt() else 0xFF6A6A6F.toInt()) and 0xFFFFFF)
                val headColor = String.format("#%06X", (if (isNightMode) 0xFFF2F2F2.toInt() else 0xFF000000.toInt()) and 0xFFFFFF)
                val linkColor = String.format("#%06X", (if (isNightMode) 0xFF5E9BFF.toInt() else 0xFF3482FF.toInt()) and 0xFFFFFF)

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
                                var color=window.getComputedStyle(el).color.match(/\d+/g);
                                if(color&&Number(color[0])>=230&&Number(color[1])>=230&&Number(color[2])>=230){
                                    el.style.setProperty('color','$bodyColor','important');
                                }
                                var hasImg=el.querySelector('img')||el.querySelector('video');
                                var hasText=el.innerText.replace(/\s/g,'').length>0;
                                if(hasImg&&!hasText){el.style.margin='0';el.style.padding='0';el.style.lineHeight='0';el.style.fontSize='0';el.style.display='block';}
                                else if(hasImg){el.style.display='block';}
                            }
                            var bb=document.querySelectorAll('.bb_img_ctn');
                            for(var j=0;j<bb.length;j++){bb[j].style.display='block';bb[j].style.lineHeight='0';bb[j].style.margin='0';}
                            if(${!isNightMode}){
                                var target='$bodyColor';
                                var targetR=parseInt(target.substring(1,3),16);
                                var targetG=parseInt(target.substring(3,5),16);
                                var targetB=parseInt(target.substring(5,7),16);
                                var normalizeImage=function(img){
                                    if(img.dataset.gaColorNormalized==='1'||!img.naturalWidth||!img.naturalHeight){return;}
                                    img.dataset.gaColorNormalized='1';
                                    try{
                                        var canvas=document.createElement('canvas');
                                        canvas.width=img.naturalWidth;
                                        canvas.height=img.naturalHeight;
                                        var ctx=canvas.getContext('2d',{willReadFrequently:true});
                                        ctx.drawImage(img,0,0);
                                        var pixels=ctx.getImageData(0,0,canvas.width,canvas.height);
                                        var values=pixels.data;
                                        var transparent=false;
                                        for(var p=3;p<values.length;p+=4){
                                            if(values[p]<250){transparent=true;break;}
                                        }
                                        if(!transparent){return;}
                                        var changed=false;
                                        for(var q=0;q<values.length;q+=4){
                                            if(values[q+3]>0&&values[q]>=230&&values[q+1]>=230&&values[q+2]>=230){
                                                values[q]=targetR;
                                                values[q+1]=targetG;
                                                values[q+2]=targetB;
                                                changed=true;
                                            }
                                        }
                                        if(changed){
                                            ctx.putImageData(pixels,0,0);
                                            img.src=canvas.toDataURL('image/png');
                                        }
                                    }catch(ignore){}
                                };
                                var images=document.querySelectorAll('img.bb_img');
                                for(var k=0;k<images.length;k++){
                                    var image=images[k];
                                    if(image.complete){normalizeImage(image);}
                                    else{image.addEventListener('load',function(){normalizeImage(this);},{once:true});}
                                }
                            }
                        });
                    </script>
                """.trimIndent()

                descriptionHtml = "<html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">$css</head><body>$rawDesc $jsScript</body></html>"
            }

            // 评价
            val summary = reviewsResp?.query_summary
            if (summary != null && summary.total_reviews > 0) {
                reviewSummary = summary.review_score_desc ?: noDataText
                reviewPercent = (summary.total_positive.toDouble() / summary.total_reviews.toDouble() * 100).toInt()
                reviewCount = summary.total_reviews
                reviews = reviewsResp.reviews ?: emptyList()
            } else {
                reviewSummary = noDataText
            }
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                android.util.Log.e("DetailActivity", "Failed to load game details for appId=$appId", e)
            }
        } finally {
            if (reviewSummary.isEmpty()) {
                reviewSummary = noDataText
            }
            isLoading = detailUiState.isLoading
        }
    }

    // ── UI ──
    val scrollState = rememberLazyListState()

    // 顶栏滑动显隐
    val topBarHeightDp = 52.dp + statusBarDp + 4.dp

    Surface(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(DesignTokens.CornerXLarge))) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {

        Crossfade(
            targetState = isLoading,
            animationSpec = tween(DesignTokens.AnimDuration),
            label = "game_detail_loading"
        ) { loading ->
        if (loading) {
            GameDetailLoadingSkeleton(topBarHeightDp)
        } else {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topBarHeightDp + 4.dp,
                    bottom = 48.dp
                )
            ) {
                item("detail_content") {
                Column {
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
                                .noRippleClickable {
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
                                    imageVector = MiuixIcons.Demibold.Play,
                                    contentDescription = stringResource(R.string.media_play),
                                    modifier = Modifier.size(DesignTokens.IconPlay),
                                    colorFilter = ColorFilter.tint(Color.White)
                                )
                            }
                        }
                    }
                }

                // ── 价格 + 评价 —— 左右双区 ──
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    cornerRadius = DesignTokens.CornerLarge
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp).height(IntrinsicSize.Max),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ── 左：价格区（折扣 2/5 + 价格 3/5）──
                        Row(
                            modifier = Modifier.weight(1f).height(IntrinsicSize.Max),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 折扣块（左半的 2/5）
                            if (discountPercent > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(0.4f).fillMaxHeight()
                                        .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                                        .background(DesignTokens.DiscountGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "-$discountPercent%",
                                        fontWeight = FontWeight.Black,
                                        fontSize = DesignTokens.TextBody1.sp,
                                        color = DesignTokens.DiscountGreenText
                                    )
                                }
                            }
                            // 价格双行（左半的 3/5）
                            Column(
                                modifier = Modifier.weight(if (discountPercent > 0) 0.6f else 1f)
                                    .padding(start = if (discountPercent > 0) 10.dp else 0.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (originalPrice.isNotEmpty()) {
                                    Text(
                                        text = originalPrice,
                                        fontSize = DesignTokens.TextCaption.sp,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                                        textDecoration = TextDecoration.LineThrough
                                    )
                                }
                                Text(
                                    text = finalPrice,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = DesignTokens.TextSubtitle.sp
                                )
                            }
                        }

                        // 竖线分隔
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                                .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.3f))
                        )
                        Spacer(Modifier.width(12.dp))

                        // ── 右：评价区（双行）──
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = reviewSummary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = DesignTokens.TextBody1.sp,
                                    color = if (reviewPercent >= 0) reviewColor(reviewPercent) else DesignTokens.ReviewDefault
                                )
                                if (reviewPercent >= 0) {
                                    Text(
                                        text = "  $reviewPercent%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = DesignTokens.TextBody1.sp,
                                        color = reviewColor(reviewPercent)
                                    )
                                }
                            }
                            if (reviewCount > 0) {
                                Text(
                                    text = stringResource(R.string.detail_reviews_count, reviewCount),
                                    fontSize = DesignTokens.TextCaption.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                                    modifier = Modifier.padding(top = 2.dp)
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
                    val canOpenDeveloper = developer.isNotBlank() &&
                        developer != stringResource(R.string.detail_unknown)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (canOpenDeveloper) {
                                    Modifier.motionClickable {
                                        context.startActivity(
                                            GameCollectionActivity.createIntent(
                                                context,
                                                GameCollectionActivity.MODE_DEVELOPER,
                                                developer
                                            )
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                imageVector = MiuixIcons.Demibold.Community,
                                contentDescription = null,
                                modifier = Modifier.size(DesignTokens.IconMd),
                                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody))
                            )
                            Text(
                                text = stringResource(R.string.detail_developer),
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
                                text = stringResource(R.string.detail_release_date),
                                fontSize = DesignTokens.TextCaption.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Image(
                                imageVector = MiuixIcons.Demibold.Months,
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
                        .heightIn(min = 56.dp)
                        .clickable { showMarkSheet = true }
                        .padding(
                            horizontal = DesignTokens.SpaceXl,
                            vertical = DesignTokens.SpaceLg
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (currentMark in GameMarks.markResIds) {
                                stringResource(currentMark)
                            } else {
                                stringResource(R.string.mark_select_status)
                            },
                            fontSize = DesignTokens.TextBody1.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentMark in GameMarks.markResIds) {
                                Color(GameMarks.colorFor(currentMark))
                            } else {
                                MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
                            }
                        )
                    }
                    Image(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(DesignTokens.IconMd),
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityHint))
                    )
                }

                // ── 标签选择器 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .then(
                            if (gameTags.isEmpty()) {
                                Modifier.motionClickable(
                                    pressedScale = 0.99f,
                                    onClick = { showTagSheet = true }
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = DesignTokens.SpaceXl),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (gameTags.isEmpty()) {
                        Text(
                            text = stringResource(R.string.tag_edit),
                            fontSize = DesignTokens.TextBody1.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier
                                .weight(1f)
                                .padding(
                                    end = DesignTokens.SpaceMd,
                                    top = DesignTokens.SpaceLg,
                                    bottom = DesignTokens.SpaceLg
                                ),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            gameTags.distinctBy { it.lowercase() }.forEach { tag ->
                                Text(
                                    text = tag,
                                    fontSize = DesignTokens.TextBody1.sp,
                                    color = DesignTokens.AccentBlue,
                                    modifier = Modifier
                                        .motionClickable {
                                            context.startActivity(
                                                GameCollectionActivity.createIntent(
                                                    context,
                                                    GameCollectionActivity.MODE_TAG,
                                                    tag
                                                )
                                            )
                                        }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(DesignTokens.ButtonHeight)
                            .heightIn(min = 56.dp)
                            .noRippleClickable { showTagSheet = true },
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Image(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.IconMd),
                            colorFilter = ColorFilter.tint(
                                MiuixTheme.colorScheme.onSurface.copy(
                                    alpha = DesignTokens.OpacityHint
                                )
                            )
                        )
                    }
                }

                // ── 本地评论 ──
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(
                                horizontal = DesignTokens.SpaceXl,
                                vertical = DesignTokens.SpaceLg
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (gameNote.isNotEmpty()) {
                                if (noteExpanded) gameNote
                                else {
                                    val preview = gameNote.replace("\n", " ").take(20)
                                    if (gameNote.length > 20) "$preview…" else preview
                                }
                            } else {
                                stringResource(R.string.note_local)
                            },
                            fontSize = DesignTokens.TextBody1.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (gameNote.isNotEmpty())
                                MiuixTheme.colorScheme.onSurface
                            else
                                MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                            modifier = Modifier
                                .weight(1f)
                                .motionClickable(
                                    pressedScale = 0.99f,
                                    onClick = { showNoteSheet = true }
                                ),
                            maxLines = if (noteExpanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Image(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            modifier = Modifier
                                .size(DesignTokens.IconMd)
                                .graphicsLayer { rotationZ = if (noteExpanded) 90f else 0f }
                                .noRippleClickable { if (gameNote.isNotEmpty()) noteExpanded = !noteExpanded },
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityHint))
                        )
                    }
                }

                // 分割线
                ActivityHistorySection(
                    kind = ActivityKind.GAME,
                    records = playRecords,
                    dimTitle = true,
                    modifier = Modifier.padding(horizontal = DesignTokens.SpaceXl)
                )
                SteamAchievementsSection(
                    appId = appId,
                    modifier = Modifier.padding(horizontal = DesignTokens.SpaceXl)
                )
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
                    text = stringResource(R.string.detail_about),
                    fontWeight = FontWeight.Bold,
                    fontSize = DesignTokens.TextBody2.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )

                // ── 简介 WebView ──
                if (descriptionHtml.isNotEmpty()) {
                    val webViewRef = remember { mutableStateOf<WebView?>(null) }
                    DisposableEffect(Unit) {
                        onDispose { webViewRef.value?.destroy() }
                    }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef.value = this
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
                }
                }
                if (reviews.isNotEmpty()) {
                    item("reviews_header") {
                    Text(
                        text = stringResource(R.string.detail_reviews_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = DesignTokens.TextBody2.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 32.dp, bottom = 12.dp)
                    )

                    // ── 评价列表 ──
                    }
                    items(reviews, key = { it.recommendationid }) { review ->
                        ReviewItem(review = review)
                    }
                }

                // 底部间距
                item("bottom_spacer") { Spacer(Modifier.height(32.dp)) }
            }
        }
        }

        // ── 顶栏叠加层（滑动自动显隐） ──
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .scrollLinkedTopBar(scrollState, topBarHeightDp)
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
                        imageVector = MiuixIcons.Demibold.Back,
                        contentDescription = stringResource(R.string.general_back),
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
                        .noRippleClickable { renameText = gameName; showRenameDialog = true }
                )
            }
        }
    } // close Box

        // ── 标记选择底部弹窗 ──
        MotionModalOverlay(
            visible = showMarkSheet,
            onDismissRequest = { showMarkSheet = false },
            contentAlignment = Alignment.BottomCenter,
            bottomSheet = true
        ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { }  // 阻止事件穿透
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.mark_select_status),
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                            fontSize = DesignTokens.TextBody1.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        // 清除标记
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable {
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
                                            DesignTokens.BorderThick,
                                            MiuixTheme.colorScheme.outline,
                                            RoundedCornerShape(DesignTokens.CornerMedium)
                                        )
                                        else Modifier
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.mark_clear),
                                fontSize = DesignTokens.TextBody1.sp,
                                color = if (currentMark == -1) DesignTokens.AccentBlue else MiuixTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        // 7 种状态
                        GameMarks.markResIds.forEach { resId ->
                            val markColor = Color(GameMarks.colorFor(resId))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .noRippleClickable {
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
                                    text = stringResource(resId),
                                    fontSize = DesignTokens.TextBody1.sp,
                                    fontWeight = if (currentMark == resId) FontWeight.Bold else FontWeight.Normal,
                                    color = markColor
                                )
                            }
                        }
                    }
                }
        }

        // ── 标签选择底部弹窗 ──
        MotionModalOverlay(
            visible = showTagSheet,
            onDismissRequest = { showTagSheet = false },
            contentAlignment = Alignment.BottomCenter,
            bottomSheet = true
        ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { }
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.tag_select),
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                            fontSize = DesignTokens.TextBody1.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        // ── 新建标签 ──
                        var newTagInput by remember { mutableStateOf("") }
                        val tagListState = rememberLazyListState()
                        val tagListScope = rememberCoroutineScope()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(IntrinsicSize.Max)) {
                            top.yukonga.miuix.kmp.basic.TextField(
                                value = newTagInput,
                                onValueChange = { newTagInput = it },
                                modifier = Modifier.weight(0.6f),
                                label = stringResource(R.string.tag_hint),
                                useLabelAsPlaceholder = true,
                                singleLine = true
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 72.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                    .background(if (newTagInput.trim().isNotEmpty()) buttonBgColor() else buttonBgColor().copy(alpha = DesignTokens.OpacityDisabled))
                                    .then(if (newTagInput.trim().isNotEmpty()) Modifier.noRippleClickable {
                                        val trimmed = newTagInput.trim()
                                        if (trimmed.isEmpty()) return@noRippleClickable
                                        val existingTagIndex = allTags.indexOfFirst { it.equals(trimmed, ignoreCase = true) }
                                        if (existingTagIndex >= 0) {
                                            android.widget.Toast.makeText(context, R.string.tag_exists, android.widget.Toast.LENGTH_SHORT).show()
                                            tagListScope.launch {
                                                tagListState.animateScrollToItem(existingTagIndex)
                                            }
                                            return@noRippleClickable
                                        }
                                        GameTags.addTag(context, trimmed)
                                        gameTags = gameTags + trimmed
                                        GameTags.setTagsForGame(context, appId, gameTags)
                                        newTagInput = ""
                                        tagRefresh++
                                    } else Modifier)
                                    .padding(horizontal = DesignTokens.SpaceXl),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = stringResource(R.string.tag_create), fontSize = DesignTokens.TextBody1.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        // ── 标签池 ──
                        if (allTags.isEmpty()) {
                            Text(
                                text = stringResource(R.string.general_no_data),
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityHint),
                                fontSize = DesignTokens.TextBody1.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        } else {
                            LazyColumn(
                                state = tagListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp)
                            ) {
                                items(allTags, key = { it.lowercase() }) { tag ->
                                    val checked = gameTags.contains(tag)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
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
                                        Text(
                                            text = tag,
                                            fontSize = DesignTokens.TextSubtitle.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Image(
                                            imageVector = MiuixIcons.Demibold.Close,
                                            contentDescription = stringResource(R.string.general_delete),
                                            colorFilter = ColorFilter.tint(DesignTokens.ErrorRed),
                                            modifier = Modifier
                                                .size(DesignTokens.IconXl)
                                                .noRippleClickable {
                                                    gameTags = gameTags.filter { it != tag }
                                                    GameTags.setTagsForGame(context, appId, gameTags)
                                                    GameTags.deleteTag(context, tag)
                                                    tagRefresh++
                                                }
                                                .padding(4.dp)
                                        )
                                    }
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
                            Text(text = stringResource(R.string.settings_save_profile), color = Color.White, fontWeight = FontWeight.Bold, fontSize = DesignTokens.TextBody1.sp)
                        }
                    }
                }
        }

        // ── 本地评论弹窗 ──
        MotionModalOverlay(
            visible = showNoteSheet,
            onDismissRequest = { showNoteSheet = false },
            contentAlignment = Alignment.BottomCenter,
            bottomSheet = true
        ) {
            var editText by remember { mutableStateOf(gameNote) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .noRippleClickable { }
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.note_local),
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                            fontSize = DesignTokens.TextBody1.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                                .background(MiuixTheme.colorScheme.surface)
                                .border(
                                    DesignTokens.BorderThin,
                                    MiuixTheme.colorScheme.outline,
                                    RoundedCornerShape(DesignTokens.CornerMedium)
                                )
                                .padding(12.dp)
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                modifier = Modifier.fillMaxSize(),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = DesignTokens.TextBody1.sp,
                                    color = MiuixTheme.colorScheme.onSurface
                                ),
                                decorationBox = { innerTextField ->
                                    if (editText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.note_hint),
                                            fontSize = DesignTokens.TextBody1.sp,
                                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityHint)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(DesignTokens.ButtonHeightSmall)
                                .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                .background(buttonBgColor())
                                .noRippleClickable {
                                    gameNote = editText.trim()
                                    GameNotes.setNote(context, appId, gameNote)
                                    showNoteSheet = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.settings_save_profile),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = DesignTokens.TextBody1.sp
                            )
                        }
                    }
                }
        }
        // ── 重命名弹窗 ──
        if (showRenameDialog) {
            Dialog(
                onDismissRequest = { showRenameDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                Card(
                    modifier = Modifier
                        .motionDialogSurface()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    cornerRadius = DesignTokens.CornerLarge
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.game_rename),
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                            fontSize = DesignTokens.TextBody1.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        TextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Box(
                                modifier = Modifier
                                    .height(DesignTokens.ButtonHeightSmall)
                                    .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                    .background(buttonBgColor())
                                    .noRippleClickable {
                                        GameNames.deleteName(context, appId)
                                        gameName = appName
                                        showRenameDialog = false
                                    }
                                    .padding(horizontal = DesignTokens.SpaceXl),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.game_restore_default),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = DesignTokens.TextBody1.sp
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .height(DesignTokens.ButtonHeightSmall)
                                    .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                    .background(if (renameText.trim().isNotEmpty()) buttonBgColor() else buttonBgColor().copy(alpha = DesignTokens.OpacityDisabled))
                                    .then(if (renameText.trim().isNotEmpty()) Modifier.noRippleClickable {
                                        val trimmed = renameText.trim()
                                        if (trimmed.isNotEmpty()) {
                                            GameNames.setName(context, appId, trimmed)
                                            gameName = trimmed
                                            showRenameDialog = false
                                        }
                                    } else Modifier)
                                    .padding(horizontal = DesignTokens.SpaceXl),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_save_profile),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = DesignTokens.TextBody1.sp
                                )
                            }
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
        java.text.SimpleDateFormat("yyyy-MM-dd",
            if (ThemeUtils.getLanguage(context) == LocaleHelper.LANG_ENGLISH) java.util.Locale.ENGLISH else java.util.Locale.CHINESE)
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
                            text = stringResource(
                                if (review.voted_up) R.string.review_recommended else R.string.review_not_recommended
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = DesignTokens.TextBody2.sp,
                            modifier = Modifier.padding(start = 4.dp),
                            color = if (review.voted_up) DesignTokens.SuccessGreen else DesignTokens.ErrorRed
                        )
                    }
                    Text(
                        text = stringResource(R.string.review_playtime, hours),
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
                    text = stringResource(R.string.review_helpful, review.votes_up),
                    fontSize = DesignTokens.TextCaption.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
