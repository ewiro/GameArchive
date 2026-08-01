package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

class BangumiSearchActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeUtils.applyTheme(this)
        setContent {
            MiuixThemeForApp {
                BangumiSearchScreen(
                    onBack = { finish() },
                    onOpenSubject = { subject ->
                        val subjectId = subject.id ?: return@BangumiSearchScreen
                        startActivity(Intent(this, BangumiDetailActivity::class.java).apply {
                            putExtra("SUBJECT_ID", subjectId)
                            putExtra("SUBJECT_NAME", subject.name.orEmpty())
                            putExtra("SUBJECT_NAME_CN", subject.name_cn.orEmpty())
                            putExtra(
                                "SUBJECT_IMAGE",
                                subject.images?.large
                                    ?: subject.images?.common
                                    ?: subject.images?.medium
                                    ?: ""
                            )
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                )
            }
        }
    }
}

@Composable
private fun BangumiSearchScreen(
    onBack: () -> Unit,
    onOpenSubject: (BangumiSubjectDetail) -> Unit
) {
    val context = LocalContext.current
    val statusBarDp = statusBarHeightDp()
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<BangumiSubjectDetail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    val showRating = UserPrefs.getBangumiRatingMode(context) == 0

    LaunchedEffect(query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            results = emptyList()
            hasSearched = false
            searchFailed = false
            isLoading = false
            return@LaunchedEffect
        }
        delay(350)
        isLoading = true
        searchFailed = false
        results = runCatching {
            GameArchiveApp.bgmService.searchSubjects(
                BangumiSubjectSearchRequest(
                    keyword = keyword,
                    filter = BangumiSubjectSearchFilter(type = listOf(2))
                )
            ).data.orEmpty().filter { it.id != null && it.type == 2 }
        }.onFailure {
            searchFailed = true
        }.getOrDefault(emptyList())
        hasSearched = true
        isLoading = false
        listState.scrollToItem(0)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarDp + 4.dp, end = 12.dp, bottom = 4.dp)
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Image(
                            imageVector = MiuixIcons.Demibold.Back,
                            contentDescription = context.getString(R.string.general_back),
                            modifier = Modifier.size(DesignTokens.IconXl),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                        )
                    }
                    Text(
                        text = context.getString(R.string.bangumi_search_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = DesignTokens.TextHeadline.sp,
                        modifier = Modifier.padding(start = DesignTokens.SpaceXs)
                    )
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.SpaceXl, vertical = DesignTokens.SpaceMd),
                    label = context.getString(R.string.bangumi_search),
                    useLabelAsPlaceholder = true,
                    singleLine = true
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = isLoading,
                        animationSpec = tween(DesignTokens.AnimDuration),
                        label = "bangumi_search_loading"
                    ) { loading ->
                        if (loading) {
                            BangumiSearchLoadingSkeleton()
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    top = DesignTokens.SpaceXs,
                                    bottom = DesignTokens.SpaceMassive
                                )
                            ) {
                                items(results, key = { it.id!! }) { subject ->
                                    BangumiSearchResult(
                                        subject = subject,
                                        showRating = showRating,
                                        modifier = Modifier.animateItem()
                                    ) {
                                        onOpenSubject(subject)
                                    }
                                }
                            }
                            when {
                                searchFailed -> SearchMessage(
                                    text = context.getString(R.string.bangumi_search_failed)
                                )
                                hasSearched && results.isEmpty() -> SearchMessage(
                                    text = context.getString(R.string.bangumi_search_no_results)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            fontSize = DesignTokens.TextBody1.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
        )
    }
}

@Composable
private fun BangumiSearchResult(
    subject: BangumiSubjectDetail,
    showRating: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val coverWidth = 80.dp
    val coverHeight = 112.dp
    val dim = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
    val displayName = subject.name_cn?.takeIf(String::isNotBlank)
        ?: subject.name.orEmpty()
    val originalName = subject.name
        ?.takeIf { it.isNotBlank() && it != displayName }
    val tags = subject.tags.orEmpty()
        .asSequence()
        .map { it.name.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(3)
        .toList()
    val score = if (showRating) extractSearchScore(subject.rating) else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .motionClickable(pressedScale = 0.985f, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(coverWidth, coverHeight)
                .clip(RoundedCornerShape(DesignTokens.SpaceSm))
                .background(MiuixTheme.colorScheme.surfaceVariant)
        ) {
            val imageUrl = subject.images?.common
                ?: subject.images?.large
                ?: subject.images?.medium
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.width(DesignTokens.SpaceLg))
        Column(
            modifier = Modifier
                .weight(1f)
                .height(coverHeight)
                .padding(vertical = DesignTokens.SpaceXxs)
        ) {
            Text(
                text = displayName,
                fontSize = DesignTokens.TextBody1.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (originalName != null) {
                Spacer(Modifier.height(DesignTokens.SpaceXs))
                Text(
                    text = originalName,
                    fontSize = DesignTokens.TextBody2.sp,
                    color = dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(DesignTokens.SpaceMd))
                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpaceXs)) {
                    tags.forEach { tag ->
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
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            val episodeCount = subject.eps?.takeIf { it > 0 }
            if (episodeCount != null) {
                Text(
                    text = context.getString(
                        R.string.bangumi_card_episode_count,
                        episodeCount
                    ),
                    fontSize = DesignTokens.TextBody2.sp,
                    color = dim
                )
            }
        }
        if (score != null && score > 0) {
            Spacer(Modifier.width(DesignTokens.SpaceMd))
            Text(
                text = String.format("%.1f", score),
                fontSize = DesignTokens.TextTitle.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    score >= 8.0 -> DesignTokens.ErrorRed
                    score >= 7.0 -> DesignTokens.PriceOrange
                    score >= 5.0 -> DesignTokens.AccentBlue
                    else -> DesignTokens.ReviewMixed
                }
            )
        }
    }
}

private fun extractSearchScore(rating: Any?): Double? {
    val value = (rating as? Map<*, *>)?.get("score")
    return (value as? Number)?.toDouble()
}
