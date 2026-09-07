package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

class BangumiSeasonActivity : ComponentActivity() {
    private val seasonViewModel by viewModels<BangumiSeasonViewModel>()

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(LocaleHelper::setLocale))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeUtils.applyTheme(this)
        setContent {
            MiuixThemeForApp {
                BangumiSeasonScreen(
                    viewModel = seasonViewModel,
                    onBack = { finish() },
                    onOpenSubject = ::openSubject
                )
            }
        }
    }

    private fun openSubject(subject: BangumiSubjectDetail) {
        val subjectId = subject.id ?: return
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
}

@Composable
private fun BangumiSeasonScreen(
    viewModel: BangumiSeasonViewModel,
    onBack: () -> Unit,
    onOpenSubject: (BangumiSubjectDetail) -> Unit
) {
    val context = LocalContext.current
    val summaries by viewModel.summaries.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val overviewListState = rememberLazyListState()
    val detailListState = rememberLazyListState()
    val selectedSeason = detail.season
    val activeListState = if (selectedSeason == null) overviewListState else detailListState
    val statusBarDp = statusBarHeightDp()
    val topBarHeightDp = statusBarDp + 56.dp
    val bottomPadding = DesignTokens.SpaceXl +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val showRating = UserPrefs.getBangumiRatingMode(context) == 0
    var seasonCount by rememberSaveable { mutableIntStateOf(INITIAL_SEASON_COUNT) }
    val seasons = remember(seasonCount) {
        bangumiSeasonsFrom(currentBangumiSeason(), seasonCount)
    }

    BackHandler(enabled = selectedSeason != null) {
        viewModel.closeSeason()
    }

    LaunchedEffect(selectedSeason) {
        if (selectedSeason != null) detailListState.scrollToItem(0)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedSeason == null) {
                SeasonOverviewList(
                    seasons = seasons,
                    summaries = summaries,
                    listState = overviewListState,
                    topPadding = topBarHeightDp,
                    bottomPadding = bottomPadding,
                    onLoadSummary = viewModel::loadSummary,
                    onOpenSeason = viewModel::openSeason,
                    onLoadEarlier = { seasonCount += SEASON_LOAD_STEP }
                )
            } else {
                SeasonSubjectList(
                    detail = detail,
                    listState = detailListState,
                    topPadding = topBarHeightDp,
                    bottomPadding = bottomPadding,
                    showRating = showRating,
                    onOpenSubject = onOpenSubject,
                    onLoadMore = viewModel::loadNextPage,
                    onRetry = viewModel::retryDetail
                )
            }

            SeasonTopBar(
                season = selectedSeason,
                listState = activeListState,
                height = topBarHeightDp,
                statusBarPadding = statusBarDp,
                onBack = {
                    if (selectedSeason == null) onBack() else viewModel.closeSeason()
                }
            )
        }
    }
}

@Composable
private fun SeasonOverviewList(
    seasons: List<BangumiSeason>,
    summaries: Map<BangumiSeason, BangumiSeasonSummaryUiState>,
    listState: LazyListState,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onLoadSummary: (BangumiSeason) -> Unit,
    onOpenSeason: (BangumiSeason) -> Unit,
    onLoadEarlier: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding)
    ) {
        itemsIndexed(
            items = seasons,
            key = { _, season -> "${season.year}_${season.startMonth}" }
        ) { _, season ->
            LaunchedEffect(season) {
                onLoadSummary(season)
            }
            val summary = summaries[season]
            if (summary == null || summary.isLoading) {
                SeasonOverviewSkeleton()
            } else {
                SeasonOverviewCard(
                    season = season,
                    summary = summary,
                    onClick = { onOpenSeason(season) }
                )
            }
        }
        item("load_earlier") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.SpaceXl, vertical = DesignTokens.SpaceLg)
                    .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                    .background(MiuixTheme.colorScheme.tertiaryContainer)
                    .motionClickable(onClick = onLoadEarlier)
                    .padding(vertical = DesignTokens.SpaceLg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.bangumi_season_load_earlier),
                    color = MiuixTheme.colorScheme.onTertiaryContainer,
                    fontSize = DesignTokens.TextBody1.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SeasonOverviewCard(
    season: BangumiSeason,
    summary: BangumiSeasonSummaryUiState,
    onClick: () -> Unit
) {
    val dim = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .motionClickable(pressedScale = 0.985f, onClick = onClick)
            .padding(horizontal = DesignTokens.SpaceXl, vertical = DesignTokens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeasonCoverCollage(
            subjects = summary.previewSubjects,
            total = summary.total ?: 0
        )
        Spacer(Modifier.width(DesignTokens.SpaceXl))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (summary.total != null) {
                    stringResource(
                        R.string.bangumi_season_quarter_with_count,
                        season.year,
                        season.startMonth,
                        summary.total
                    )
                } else {
                    stringResource(
                        R.string.bangumi_season_quarter,
                        season.year,
                        season.startMonth
                    )
                },
                fontSize = DesignTokens.TextTitle.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(DesignTokens.SpaceMd))
            Text(
                text = if (summary.failed) {
                    stringResource(R.string.bangumi_season_load_failed)
                } else {
                    stringResource(
                        R.string.bangumi_season_date_range,
                        season.startDate,
                        season.endInclusiveDate
                    )
                },
                fontSize = DesignTokens.TextBody2.sp,
                color = if (summary.failed) MiuixTheme.colorScheme.error else dim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(DesignTokens.SpaceLg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.bangumi_season_category),
                    modifier = Modifier
                        .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                        .background(MiuixTheme.colorScheme.secondaryContainer)
                        .padding(
                            horizontal = DesignTokens.SpaceSm,
                            vertical = DesignTokens.SpaceXxs
                        ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = DesignTokens.TextCaption.sp
                )
                Spacer(Modifier.weight(1f))
                Image(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(DesignTokens.IconMd),
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions)
                )
            }
        }
    }
}

@Composable
private fun SeasonCoverCollage(
    subjects: List<BangumiSubjectDetail>,
    total: Int
) {
    val context = LocalContext.current
    val collageSize = 124.dp
    val cellSize = collageSize / 2
    val corner = RoundedCornerShape(DesignTokens.CornerMedium)
    Box(modifier = Modifier.size(132.dp)) {
        repeat(2) { layer ->
            Box(
                modifier = Modifier
                    .size(collageSize)
                    .offset(x = (8 - layer * 4).dp, y = (8 - layer * 4).dp)
                    .clip(corner)
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .border(DesignTokens.BorderThin, MiuixTheme.colorScheme.outline, corner)
            )
        }
        Column(
            modifier = Modifier
                .size(collageSize)
                .clip(corner)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
        ) {
            repeat(2) { row ->
                Row {
                    repeat(2) { column ->
                        val index = row * 2 + column
                        if (index == 3) {
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .background(MiuixTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.bangumi_season_count_short,
                                        total
                                    ),
                                    color = MiuixTheme.colorScheme.onTertiaryContainer,
                                    fontSize = DesignTokens.TextTitle.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            val subject = subjects.getOrNull(index)
                            val imageUrl = subject?.images?.common
                                ?: subject?.images?.large
                                ?: subject?.images?.medium
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                if (!imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonOverviewSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.SpaceXl, vertical = DesignTokens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoadingSkeletonBlock(
            modifier = Modifier.size(124.dp),
            cornerRadius = DesignTokens.CornerMedium
        )
        Spacer(Modifier.width(DesignTokens.SpaceXl))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.SpaceMd)
        ) {
            LoadingSkeletonBlock(Modifier.fillMaxWidth(0.9f).height(18.dp))
            LoadingSkeletonBlock(Modifier.fillMaxWidth(0.72f).height(13.dp))
            Spacer(Modifier.height(DesignTokens.SpaceLg))
            LoadingSkeletonBlock(Modifier.width(52.dp).height(22.dp))
        }
    }
}

@Composable
private fun SeasonSubjectList(
    detail: BangumiSeasonDetailUiState,
    listState: LazyListState,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    showRating: Boolean,
    onOpenSubject: (BangumiSubjectDetail) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    val subjects = detail.subjects
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding)
    ) {
        if (subjects != null) {
            itemsIndexed(
                items = subjects,
                key = { index, subject -> subject.id ?: "season_missing_$index" }
            ) { index, subject ->
                if (index >= subjects.lastIndex - 4 && detail.hasMore) {
                    LaunchedEffect(detail.season, detail.nextOffset) {
                        onLoadMore()
                    }
                }
                BangumiSubjectListItem(
                    subject = subject,
                    showRating = showRating,
                    collectionType = null,
                    modifier = Modifier.animateItem()
                ) {
                    onOpenSubject(subject)
                }
            }
            if (detail.isLoadingMore) {
                item("loading_more") {
                    SeasonSubjectLoadingRow()
                }
            }
            if (detail.failed && subjects.isNotEmpty()) {
                item("load_more_failed") {
                    SeasonRetryButton(onRetry = onLoadMore)
                }
            }
        }
    }

    when {
        detail.isLoading && subjects == null -> Box(
            Modifier.fillMaxSize().padding(top = topPadding)
        ) {
            BangumiSearchLoadingSkeleton()
        }
        detail.failed && subjects == null -> SeasonCenteredMessage(
            text = stringResource(R.string.bangumi_season_load_failed),
            action = stringResource(R.string.general_retry),
            onAction = onRetry
        )
        subjects?.isEmpty() == true -> SeasonCenteredMessage(
            text = stringResource(R.string.bangumi_season_empty)
        )
    }
}

@Composable
private fun SeasonSubjectLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.SpaceXl, vertical = DesignTokens.SpaceMd)
    ) {
        LoadingSkeletonBlock(
            modifier = Modifier.width(80.dp).height(112.dp),
            cornerRadius = DesignTokens.CornerMedium
        )
        Spacer(Modifier.width(DesignTokens.SpaceLg))
        Column(
            modifier = Modifier.weight(1f).height(112.dp),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.SpaceMd)
        ) {
            LoadingSkeletonBlock(Modifier.fillMaxWidth(0.82f).height(15.dp))
            LoadingSkeletonBlock(Modifier.fillMaxWidth(0.56f).height(12.dp))
        }
    }
}

@Composable
private fun SeasonCenteredMessage(
    text: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = DesignTokens.TextBody1.sp
            )
            if (action != null && onAction != null) {
                Spacer(Modifier.height(DesignTokens.SpaceLg))
                SeasonRetryButton(label = action, onRetry = onAction)
            }
        }
    }
}

@Composable
private fun SeasonRetryButton(
    label: String = stringResource(R.string.general_retry),
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.CornerLarge))
            .background(MiuixTheme.colorScheme.tertiaryContainer)
            .motionClickable(onClick = onRetry)
            .padding(horizontal = DesignTokens.SpaceXl, vertical = DesignTokens.SpaceMd),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onTertiaryContainer,
            fontSize = DesignTokens.TextBody1.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SeasonTopBar(
    season: BangumiSeason?,
    listState: LazyListState,
    height: androidx.compose.ui.unit.Dp,
    statusBarPadding: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scrollLinkedTopBar(listState, height)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarPadding + 4.dp, end = 12.dp, bottom = 4.dp)
                .height(48.dp),
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
                text = if (season == null) {
                    stringResource(R.string.bangumi_season_title)
                } else {
                    stringResource(
                        R.string.bangumi_season_quarter_title,
                        season.year,
                        season.startMonth
                    )
                },
                fontWeight = FontWeight.Bold,
                fontSize = DesignTokens.TextHeadline.sp,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = DesignTokens.SpaceXs)
            )
        }
    }
}

private const val INITIAL_SEASON_COUNT = 8
private const val SEASON_LOAD_STEP = 8
