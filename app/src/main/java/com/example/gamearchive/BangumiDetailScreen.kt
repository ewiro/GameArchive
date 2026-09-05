package com.example.gamearchive

import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.*
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

private data class BangumiDetailValue(
    val text: String,
    val label: String? = null,
    val person: BangumiPerson? = null
)

private data class BangumiDetailRow(
    val key: String,
    val values: List<BangumiDetailValue>
)

private data class BangumiCharacterCardInfo(
    val chineseName: String = ""
)

@Composable
internal fun BangumiDetailScreen(
    subjectId: Int,
    subjectName: String,
    subjectNameCn: String,
    subjectImage: String,
    initialCoverImage: ImageBitmap? = null,
    transitionSourceRect: Rect? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val detailViewModel: BangumiDetailViewModel = viewModel()
    val detailUiState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val episodeDecreaseDescription = stringResource(R.string.bangumi_episode_decrease)
    val episodeIncreaseDescription = stringResource(R.string.bangumi_episode_increase)
    val statusBarDp = statusBarHeightDp()
    val topBarHeightDp = statusBarDp + 56.dp
    val detailScrollState = rememberScrollState()
    val detailDensity = LocalDensity.current
    val detailWindowWidth = with(detailDensity) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val detailCoverWidth = (
        detailWindowWidth - BANGUMI_GRID_HORIZONTAL_PADDING * 2 -
            BANGUMI_GRID_ITEM_SPACING * 2
        ) / 3f
    val detailCoverHeight = detailCoverWidth / PORTRAIT_COVER_ASPECT_RATIO
    val initialCoverPainter = remember(initialCoverImage) {
        initialCoverImage?.let(::BitmapPainter)
    }
    val hasCoverTransition = initialCoverImage != null && transitionSourceRect != null
    val coverTransitionProgress = remember(subjectId, hasCoverTransition) {
        Animatable(if (hasCoverTransition) 0f else 1f)
    }
    var coverTransitionFinished by remember(subjectId, hasCoverTransition) {
        mutableStateOf(!hasCoverTransition)
    }
    LaunchedEffect(subjectId, hasCoverTransition) {
        if (hasCoverTransition) {
            coverTransitionProgress.snapTo(0f)
            withFrameNanos { }
            coverTransitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = DesignTokens.ExpandDuration,
                    easing = FastOutSlowInEasing
                )
            )
            coverTransitionFinished = true
        }
    }
    val showEmbeddedCover = !hasCoverTransition || coverTransitionFinished
    val sequelCoverGap = DesignTokens.SpaceXs + with(detailDensity) {
        DesignTokens.TextBody1.sp.toDp()
    }

    val detail = detailUiState.detail
    val subjectPersons = detailUiState.persons
    val subjectCharacters = detailUiState.characters
    val sequelSubjects = remember(detailUiState.relatedSubjects) {
        detailUiState.relatedSubjects.filter { it.type == 2 && it.relation?.trim() in setOf("续集", "續集") }
    }
    val characterChineseNames = remember(detailUiState.legacySubject) {
        detailUiState.legacySubject?.crt.orEmpty().mapNotNull { character ->
            val id = character.id ?: return@mapNotNull null
            val name = character.name_cn?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            id to name
        }.toMap()
    }
    val characterCardInfo = remember { mutableStateMapOf<Int, BangumiCharacterCardInfo>() }
    val actorChineseNames = remember { mutableStateMapOf<Int, String>() }
    val myCollection = detailUiState.collection.value
    val isLoading = detailUiState.isLoading
    val isCollectionLoaded = detailUiState.collection.isLoaded
    val isSaving = detailUiState.isSaving
    val draftType = detailUiState.draft.type
    val draftRate = detailUiState.draft.rate
    val draftComment = detailUiState.draft.comment
    val draftEpisodeProgress = detailUiState.draft.episodeProgress
    val mainEpisodeCount = detailUiState.collection.episodeCount
    val mainEpisodes = detailUiState.collection.episodes
    val isEpisodeProgressUnavailable = detailUiState.collection.isEpisodeProgressUnavailable
    val myTagSuggestions = detailUiState.tagSuggestions
    val isMyTagsLoading = detailUiState.isTagsLoading
    var draftTags by remember(subjectId) { mutableStateOf(TextFieldValue(detailUiState.draft.tags)) }
    var animateLoadingSkeleton by remember(initialCoverImage) {
        mutableStateOf(initialCoverImage == null)
    }
    var isTagInputFocused by remember { mutableStateOf(false) }
    var tagInputRect by remember { mutableStateOf(Rect.Zero) }
    var isStatusDropdownVisible by remember { mutableStateOf(false) }
    var statusDropdownRect by remember { mutableStateOf(Rect.Zero) }
    var isProgressExpanded by remember { mutableStateOf(false) }
    var activityHistoryRevision by remember { mutableIntStateOf(0) }
    var editingWatchRecord by remember { mutableStateOf<ItemActivityRecord?>(null) }
    var editingWatchAmount by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(subjectId) {
        detailViewModel.load(subjectId, context, subjectName, subjectNameCn, subjectImage)
    }
    LaunchedEffect(initialCoverImage) {
        if (initialCoverImage != null) {
            delay(160)
            animateLoadingSkeleton = true
        }
    }
    LaunchedEffect(detailUiState.draft.tags) {
        val text = detailUiState.draft.tags
        if (draftTags.text != text) draftTags = TextFieldValue(text, TextRange(text.length))
    }
    val message = detailUiState.messages.firstOrNull()
    LaunchedEffect(message?.id) {
        if (message != null) {
            Toast.makeText(context, message.resourceId,
                if (message.longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            detailViewModel.consumeMessage(message.id)
        }
    }

    val displayName = subjectNameCn.ifEmpty { subjectName }
    val score = normalizeBangumiScore(detail?.rating)
    val ratingMode = UserPrefs.getBangumiRatingMode(context)
    val watchRecords by produceState<List<ItemActivityRecord>>(
        initialValue = emptyList(),
        subjectId,
        activityHistoryRevision,
        detailUiState.saveRevision
    ) {
        value = withContext(Dispatchers.IO) {
            ActivityStats.getAnimeRecords(context, subjectId)
        }
    }
    val showRating = ratingMode == 0  // 0=展示评分, 1=仅我的评分(详情页无), 2=不展示
    val dim = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
    val typeLabels = listOf(
        1 to stringResource(R.string.bangumi_wish),
        3 to stringResource(R.string.bangumi_doing),
        2 to stringResource(R.string.bangumi_done),
        4 to stringResource(R.string.bangumi_on_hold),
        5 to stringResource(R.string.bangumi_dropped)
    )

    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = coverTransitionProgress.value
                },
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface)
            ) {
                // 顶栏
                Crossfade(
                    targetState = isLoading,
                    modifier = Modifier.fillMaxSize(),
                    animationSpec = tween(DesignTokens.AnimDuration),
                    label = "anime_detail_loading"
                ) { loading ->
                if (loading) {
                    Box(Modifier.fillMaxSize().padding(top = topBarHeightDp)) {
                        AnimeDetailLoadingSkeleton(
                            coverImageUrl = subjectImage,
                            coverImage = initialCoverImage,
                            coverWidth = detailCoverWidth,
                            coverHeight = detailCoverHeight,
                            coverCornerRadius = BANGUMI_GRID_COVER_CORNER,
                            showCoverContent = showEmbeddedCover,
                            animated = animateLoadingSkeleton
                        )
                    }
                } else if (detail != null) {
                    val d = detail
                    val chapterCount = mainEpisodes.size.takeIf { it > 0 }
                        ?: mainEpisodeCount
                        ?: d.eps?.takeIf { it > 0 }
                    val episodeValueText = chapterCount?.let {
                        stringResource(R.string.bangumi_card_episode_count, it)
                    }
                    val episodeCountLabel = stringResource(
                        R.string.bangumi_detail_episode_count_label
                    )
                    val detailRows = remember(
                        d,
                        subjectPersons,
                        chapterCount,
                        episodeValueText,
                        episodeCountLabel
                    ) {
                        val infoboxRows = d.infobox.orEmpty().mapNotNull { item ->
                            val values = flattenBangumiInfoboxValue(item.value).map {
                                BangumiDetailValue(text = it.text, label = it.label)
                            }
                            if (values.isEmpty()) null else BangumiDetailRow(
                                key = item.key.trim(),
                                values = values
                            )
                        }.toMutableList()
                        if (chapterCount != null) {
                            val episodeKeys = setOf("话数", "話数", "集数", "episode", "episodes")
                            val episodeValue = requireNotNull(episodeValueText)
                            val episodeIndex = infoboxRows.indexOfFirst {
                                it.key.lowercase() in episodeKeys
                            }
                            if (episodeIndex >= 0) {
                                infoboxRows[episodeIndex] = infoboxRows[episodeIndex].copy(
                                    values = listOf(BangumiDetailValue(episodeValue))
                                )
                            } else {
                                infoboxRows.add(
                                    BangumiDetailRow(
                                        key = episodeCountLabel,
                                        values = listOf(BangumiDetailValue(episodeValue))
                                    )
                                )
                            }
                        }
                        val personGroups = subjectPersons
                            .asSequence()
                            .filter { !it.relation.isNullOrBlank() && !it.name.isNullOrBlank() }
                            .groupBy { it.relation.orEmpty().trim() }
                        personGroups.forEach { (relation, persons) ->
                            val values = persons
                                .distinctBy { it.id ?: it.name }
                                .mapNotNull { person ->
                                    person.name?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                        BangumiDetailValue(text = it, person = person)
                                    }
                                }
                            if (values.isNotEmpty()) {
                                val existingIndex = infoboxRows.indexOfFirst {
                                    it.key.equals(relation, ignoreCase = true)
                                }
                                if (existingIndex >= 0) {
                                    infoboxRows[existingIndex] =
                                        infoboxRows[existingIndex].copy(values = values)
                                } else {
                                    infoboxRows.add(BangumiDetailRow(relation, values))
                                }
                            }
                        }
                        val productionIndex = infoboxRows.indexOfFirst {
                            isAnimationProductionKey(it.key)
                        }
                        val directorIndex = infoboxRows.indexOfFirst {
                            isDirectorInfoboxKey(it.key)
                        }
                        if (productionIndex >= 0 && directorIndex >= 0 &&
                            productionIndex != directorIndex + 1
                        ) {
                            val productionRow = infoboxRows.removeAt(productionIndex)
                            val updatedDirectorIndex = infoboxRows.indexOfFirst {
                                isDirectorInfoboxKey(it.key)
                            }
                            infoboxRows.add(updatedDirectorIndex + 1, productionRow)
                        }
                        infoboxRows
                    }
                    val voiceActors = remember(subjectCharacters) {
                        subjectCharacters
                            .mapNotNull { character ->
                                character.actors.orEmpty()
                                    .filter { actor ->
                                        !actor.name.isNullOrBlank() &&
                                            actor.career.orEmpty().any {
                                                it.equals("seiyu", ignoreCase = true)
                                            }
                                    }
                                    .firstOrNull()
                                    ?.let { actor -> character to actor }
                            }
                    }
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(detailScrollState)
                            .imePadding()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = topBarHeightDp + 8.dp,
                                bottom = 8.dp
                            )
                    ) {
                        // 封面 + 基本信息
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // 封面
                            Box(
                                modifier = Modifier
                                    .size(
                                        width = detailCoverWidth,
                                        height = detailCoverHeight
                                    )
                                    .clip(RoundedCornerShape(BANGUMI_GRID_COVER_CORNER))
                                    .graphicsLayer {
                                        alpha = if (showEmbeddedCover) 1f else 0f
                                    }
                            ) {
                                val coverUrl = subjectImage.ifBlank {
                                    d.images?.large ?: d.images?.common.orEmpty()
                                }
                                if (initialCoverPainter != null) {
                                    Image(
                                        painter = initialCoverPainter,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (coverUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            // 信息
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = detailCoverHeight)
                            ) {
                                Text(
                                    text = d.name ?: subjectName,
                                    fontSize = DesignTokens.TextTitle.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!d.name_cn.isNullOrEmpty() && d.name_cn != d.name) {
                                    Text(
                                        text = d.name_cn,
                                        fontSize = DesignTokens.TextBody1.sp,
                                        color = dim,
                                        maxLines = 1
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                if (showRating && score != null && score > 0) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = String.format(Locale.ROOT, "%.1f", score),
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = bangumiScoreColor(score)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = bangumiGrade(score),
                                            fontSize = 12.sp,
                                            color = dim
                                        )
                                    }
                                    Spacer(Modifier.height(DesignTokens.SpaceXs))
                                }
                                if (sequelSubjects.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(88.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Column(modifier = Modifier.fillMaxHeight()) {
                                            Text(
                                                text = stringResource(
                                                    R.string.bangumi_sequel_label
                                                ),
                                                fontSize = DesignTokens.TextBody1.sp,
                                                color = dim
                                            )
                                            Spacer(Modifier.weight(1f))
                                            if (chapterCount != null) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.bangumi_episode_total,
                                                        chapterCount
                                                    ),
                                                    fontSize = DesignTokens.TextBody1.sp,
                                                    color = dim,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(sequelCoverGap))
                                        LazyRow(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                            horizontalArrangement = Arrangement.spacedBy(
                                                DesignTokens.SpaceXs
                                            )
                                        ) {
                                            items(
                                                items = sequelSubjects,
                                                key = BangumiRelatedSubject::id
                                            ) { sequel ->
                                                val sequelName = sequel.name_cn
                                                    ?.takeIf(String::isNotBlank)
                                                    ?: sequel.name
                                                val sequelImage = sequel.images?.small
                                                    ?: sequel.images?.grid
                                                    ?: sequel.images?.common
                                                    ?: sequel.images?.large
                                                    .orEmpty()
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 64.dp, height = 88.dp)
                                                        .clip(
                                                            RoundedCornerShape(
                                                                DesignTokens.CornerMedium
                                                            )
                                                        )
                                                        .motionClickable {
                                                            context.startActivity(
                                                                Intent(
                                                                    context,
                                                                    BangumiDetailActivity::class.java
                                                                ).apply {
                                                                    putExtra(
                                                                        "SUBJECT_ID",
                                                                        sequel.id
                                                                    )
                                                                    putExtra(
                                                                        "SUBJECT_NAME",
                                                                        sequel.name
                                                                    )
                                                                    putExtra(
                                                                        "SUBJECT_NAME_CN",
                                                                        sequel.name_cn.orEmpty()
                                                                    )
                                                                    putExtra(
                                                                        "SUBJECT_IMAGE",
                                                                        sequelImage
                                                                    )
                                                                }
                                                            )
                                                        },
                                                    contentAlignment = Alignment.TopCenter
                                                ) {
                                                    if (sequelImage.isNotEmpty()) {
                                                        AsyncImage(
                                                            model = ImageRequest.Builder(context)
                                                                .data(sequelImage)
                                                                .crossfade(true)
                                                                .build(),
                                                            contentDescription = stringResource(
                                                                R.string.bangumi_sequel_cover_description,
                                                                sequelName
                                                            ),
                                                            modifier = Modifier
                                                                .size(
                                                                    width = 60.dp,
                                                                    height = 88.dp
                                                                )
                                                                .clip(
                                                                    RoundedCornerShape(
                                                                        DesignTokens.CornerMedium
                                                                    )
                                                                ),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                    if (chapterCount != null) {
                                        Text(
                                            text = stringResource(
                                                R.string.bangumi_episode_total,
                                                chapterCount
                                            ),
                                            fontSize = DesignTokens.TextBody1.sp,
                                            color = dim,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (!d.date.isNullOrEmpty()) {
                                    val isMovie = d.name?.contains("剧场版") == true ||
                                        d.name_cn?.contains("剧场版") == true ||
                                        d.name?.contains("电影") == true ||
                                        d.name_cn?.contains("电影") == true
                                    val dateLabel = if (isMovie) "上映日期" else "放送日期"
                                    Text(text = "$dateLabel：${d.date}", fontSize = DesignTokens.TextBody1.sp, color = dim)
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // 我的评价（需授权）
                        val authorized = UserPrefs.getBangumiAccessToken(context).isNotEmpty()
                        if (authorized) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ProgressSectionHeader(
                                    expanded = isProgressExpanded,
                                    onToggle = { isProgressExpanded = !isProgressExpanded }
                                )
                                ExpandableSectionContent(expanded = isProgressExpanded) {
                                    if (!isCollectionLoaded) {
                                        BangumiCollectionLoadingSkeleton()
                                    } else {
                                    Column(
                                        modifier = Modifier.padding(
                                            start = DesignTokens.SpaceXl,
                                            end = DesignTokens.SpaceXl,
                                            bottom = DesignTokens.SpaceXl
                                        )
                                    ) {
                                    Spacer(Modifier.height(DesignTokens.SpaceXxl / 4f))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { coords ->
                                                statusDropdownRect = coords.positionInWindow().let {
                                                    Rect(
                                                        it.x,
                                                        it.y,
                                                        it.x + coords.size.width,
                                                        it.y + coords.size.height
                                                    )
                                                }
                                            }
                                            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                                            .noRippleClickable { isStatusDropdownVisible = true }
                                            .height(DesignTokens.IconXl),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.bangumi_collection_status),
                                            fontSize = DesignTokens.TextBody1.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = typeLabels.firstOrNull {
                                                it.first == draftType
                                            }?.second.orEmpty(),
                                            fontSize = DesignTokens.TextBody1.sp,
                                            color = dim
                                        )
                                        Spacer(Modifier.width(DesignTokens.SpaceXs))
                                        DropdownArrowEndAction(actionColor = dim)
                                    }

                                    if (mainEpisodes.isNotEmpty()) {
                                        Spacer(Modifier.height(DesignTokens.SpaceXxl / 4f))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(DesignTokens.ButtonHeight),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.bangumi_episode_progress),
                                                fontSize = DesignTokens.TextBody1.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MiuixTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Box(
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val canDecrease = draftEpisodeProgress > 0
                                                    Box(
                                                        modifier = Modifier
                                                            .size(DesignTokens.ButtonHeight)
                                                            .semantics {
                                                                contentDescription = episodeDecreaseDescription
                                                            }
                                                            .noRippleClickable {
                                                                if (canDecrease) detailViewModel.updateEpisodeProgress(draftEpisodeProgress - 1)
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "−",
                                                            fontSize = DesignTokens.TextHeadline.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MiuixTheme.colorScheme.onSurface.copy(
                                                                alpha = if (canDecrease) 1f
                                                                else DesignTokens.OpacityDisabled
                                                            )
                                                        )
                                                    }
                                                    Text(
                                                        text = stringResource(
                                                            R.string.bangumi_episode_compact,
                                                            draftEpisodeProgress,
                                                            mainEpisodes.size
                                                        ),
                                                        fontSize = DesignTokens.TextBody1.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = dim
                                                    )
                                                    val canIncrease =
                                                        draftEpisodeProgress < mainEpisodes.size
                                                    Box(
                                                        modifier = Modifier
                                                            .width(DesignTokens.IconXl)
                                                            .height(DesignTokens.ButtonHeight),
                                                        contentAlignment = Alignment.CenterEnd
                                                    ) {
                                                        Text(
                                                            text = "+",
                                                            fontSize = DesignTokens.TextHeadline.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MiuixTheme.colorScheme.onSurface.copy(
                                                                alpha = if (canIncrease) 1f
                                                                else DesignTokens.OpacityDisabled
                                                            )
                                                        )
                                                    }
                                                }
                                                val canIncrease =
                                                    draftEpisodeProgress < mainEpisodes.size
                                                Box(
                                                    modifier = Modifier
                                                        .size(DesignTokens.ButtonHeight)
                                                        .align(Alignment.CenterEnd)
                                                        .semantics {
                                                            contentDescription = episodeIncreaseDescription
                                                        }
                                                        .noRippleClickable {
                                                            if (canIncrease) detailViewModel.updateEpisodeProgress(draftEpisodeProgress + 1)
                                                        }
                                                )
                                            }
                                        }
                                    } else if (isEpisodeProgressUnavailable || draftType == 0) {
                                        Spacer(Modifier.height(DesignTokens.SpaceXxl / 4f))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(DesignTokens.ButtonHeight),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.bangumi_episode_progress),
                                                fontSize = DesignTokens.TextBody1.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MiuixTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = stringResource(
                                                    R.string.bangumi_episode_progress_unavailable
                                                ),
                                                fontSize = DesignTokens.TextBody2.sp,
                                                color = dim
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(DesignTokens.SpaceXxl / 4f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = stringResource(R.string.bangumi_my_rating),
                                            fontSize = DesignTokens.TextBody1.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .height(DesignTokens.IconXl)
                                                .wrapContentHeight(Alignment.CenterVertically)
                                        )
                                        Spacer(Modifier.width(DesignTokens.SpaceMd))
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(DesignTokens.ButtonHeight)
                                        ) {
                                            val selectedRatingColor = when (draftRate) {
                                                in 1..4 -> DesignTokens.ReviewMixed
                                                in 5..6 -> DesignTokens.AccentBlue
                                                in 7..8 -> DesignTokens.PriceOrange
                                                else -> DesignTokens.ErrorRed
                                            }
                                            for (i in 1..10) {
                                                val selected = i <= draftRate
                                                val selectedGradeRes = if (i == draftRate) {
                                                    bangumiPersonalGradeRes(draftRate)
                                                } else {
                                                    null
                                                }
                                                val ratingDescription = stringResource(
                                                    R.string.bangumi_rating_value,
                                                    i
                                                )
                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .semantics {
                                                            contentDescription = ratingDescription
                                                        }
                                                        .noRippleClickable {
                                                            detailViewModel.updateRate(if (draftRate == i) 0 else i)
                                                        },
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Top
                                                ) {
                                                    Box(
                                                        modifier = Modifier.height(DesignTokens.IconXl),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        SoftRatingStar(
                                                            selected = selected,
                                                            selectedColor = selectedRatingColor
                                                        )
                                                    }
                                                    Spacer(Modifier.height(DesignTokens.SpaceXxs))
                                                    Text(
                                                        text = selectedGradeRes?.let {
                                                            stringResource(it)
                                                        }.orEmpty(),
                                                        modifier = Modifier.wrapContentWidth(unbounded = true),
                                                        color = MiuixTheme.colorScheme.onSurface.copy(
                                                            alpha = DesignTokens.OpacityBody
                                                        ),
                                                        fontSize = DesignTokens.TextCaption.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(DesignTokens.SpaceXxl))

                                    TextField(
                                        value = draftTags,
                                        onValueChange = {
                                            draftTags = it
                                            detailViewModel.updateTags(it.text)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { coords ->
                                                tagInputRect = coords.positionInWindow().let {
                                                    Rect(
                                                        it.x,
                                                        it.y,
                                                        it.x + coords.size.width,
                                                        it.y + coords.size.height
                                                    )
                                                }
                                            }
                                            .onFocusChanged {
                                                isTagInputFocused = it.isFocused
                                                if (it.isFocused) detailViewModel.loadTagSuggestions()
                                            },
                                        label = stringResource(R.string.bangumi_tags_hint),
                                        useLabelAsPlaceholder = true,
                                        singleLine = true
                                    )
                                    Spacer(Modifier.height(DesignTokens.SpaceLg))

                                    TextField(
                                        value = draftComment,
                                        onValueChange = detailViewModel::updateComment,
                                        modifier = Modifier.fillMaxWidth(),
                                        label = stringResource(R.string.bangumi_comment_hint),
                                        useLabelAsPlaceholder = true,
                                        singleLine = false
                                    )
                                    Spacer(Modifier.height(DesignTokens.SpaceXxl))

                                    val saveButtonColor by animateColorAsState(
                                        targetValue = if (isSaving || draftType == 0) {
                                            buttonBgColor().copy(
                                                alpha = DesignTokens.OpacityDisabled
                                            )
                                        } else {
                                            buttonBgColor()
                                        },
                                        animationSpec = tween(DesignTokens.AnimDuration),
                                        label = "bangumi_save_button_color"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(DesignTokens.ButtonHeight)
                                            .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                            .background(saveButtonColor)
                                            .motionClickable(
                                                enabled = !isSaving && draftType != 0,
                                                pressedScale = 0.98f,
                                                onClick = detailViewModel::saveCollection
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Crossfade(
                                            targetState = isSaving,
                                            animationSpec = tween(DesignTokens.AnimDuration),
                                            label = "bangumi_save_text"
                                        ) { saving ->
                                            Text(
                                                text = stringResource(
                                                    if (saving) R.string.bangumi_saving
                                                    else R.string.bangumi_save_collection
                                                ),
                                                fontSize = DesignTokens.TextBody1.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    }
                                    }
                                }
                            }
                            Spacer(Modifier.height(DesignTokens.SpaceMd))
                        } else if (!authorized) {
                            Text(
                                text = stringResource(R.string.bangumi_authorization_required),
                                fontSize = DesignTokens.TextBody1.sp,
                                color = dim
                            )
                            Spacer(Modifier.height(DesignTokens.SpaceXxl))
                        }

                        // 标签
                        ActivityHistorySection(
                            kind = ActivityKind.ANIME,
                            records = watchRecords,
                            titleFontSize = DesignTokens.TextSubtitle.sp,
                            onRecordClick = { record ->
                                editingWatchRecord = record
                                editingWatchAmount = formatEpisodeAmount(record.amount)
                            }
                        )
                        Spacer(Modifier.height(DesignTokens.SpaceXxl))

                        if (!d.tags.isNullOrEmpty()) {
                            Text(
                                text = stringResource(R.string.bangumi_tags_title),
                                fontSize = DesignTokens.TextSubtitle.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                d.tags.orEmpty().forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                                            .background(MiuixTheme.colorScheme.secondaryContainer)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = tag.name,
                                            fontSize = 12.sp,
                                            color = dim
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // 简介
                        if (!d.summary.isNullOrEmpty()) {
                            Text(
                                text = stringResource(R.string.bangumi_summary_title),
                                fontSize = DesignTokens.TextSubtitle.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            // WebView 渲染简介
                            val isDarkWebView = isSystemInDarkTheme()
                            val summaryWebView = remember(d.id) {
                                mutableStateOf<android.webkit.WebView?>(null)
                            }
                            DisposableEffect(d.id) {
                                onDispose {
                                    summaryWebView.value?.destroy()
                                    summaryWebView.value = null
                                }
                            }
                            AndroidView(
                                factory = { ctx ->
                                    android.webkit.WebView(ctx).apply {
                                        summaryWebView.value = this
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                                        )
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        isVerticalScrollBarEnabled = false
                                        settings.javaScriptEnabled = false
                                        val cssText = if (isDarkWebView) "#CCCCCC" else "#333333"
                                        val summaryHtml = d.summary
                                            .replace("\r\n", "\n")
                                            .split("\n")
                                            .filter { it.isNotBlank() }
                                            .joinToString("") { "<p style=\"text-indent:1em;margin:0.5em 0\">$it</p>" }
                                        val html = """
                                            <html><head><style>
                                                body { font-size: 14px; line-height:1.6; color:$cssText; background:transparent;
                                                    padding:8px 0; margin:0; font-family:sans-serif; }
                                                a { color:#3482FF; }
                                            </style></head><body>$summaryHtml</body></html>
                                        """.trimIndent()
                                        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 100.dp)
                            )
                        }

                        if (voiceActors.isNotEmpty()) {
                            Spacer(Modifier.height(DesignTokens.SpaceXxl))
                            Text(
                                text = stringResource(R.string.bangumi_characters_cast_title),
                                fontSize = DesignTokens.TextSubtitle.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(DesignTokens.SpaceMd))
                            val castListState = rememberLazyListState()
                            Box(modifier = Modifier.fillMaxWidth()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                state = castListState,
                                horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpaceLg)
                            ) {
                                items(
                                    items = voiceActors,
                                    key = { (character, actor) ->
                                        "${character.id ?: character.name}_${actor.id ?: actor.name}"
                                    }
                                ) { (character, actor) ->
                                    LaunchedEffect(character.id, actor.id) {
                                        coroutineScope {
                                            val characterInfoDeferred = character.id
                                                ?.takeIf { !characterCardInfo.containsKey(it) }
                                                ?.let { characterId ->
                                                    async {
                                                        val info = runCatchingCancellable {
                                                            GameArchiveApp.bgmService
                                                                .getCharacter(characterId)
                                                        }.getOrNull()?.let { characterDetail ->
                                                            BangumiCharacterCardInfo(
                                                                chineseName = characterDetail.name_cn
                                                                    ?.takeIf { it.isNotBlank() }
                                                                    ?: bangumiChineseName(
                                                                        characterDetail.infobox
                                                                    )
                                                            )
                                                        } ?: BangumiCharacterCardInfo()
                                                        characterId to info
                                                    }
                                                }
                                            val actorNameDeferred = actor.id
                                                ?.takeIf { !actorChineseNames.containsKey(it) }
                                                ?.let { personId ->
                                                    async {
                                                        val loadedName = runCatchingCancellable {
                                                            GameArchiveApp.bgmService.getPerson(personId)
                                                        }.getOrNull()?.let { personDetail ->
                                                            bangumiChineseName(personDetail.infobox)
                                                        }.orEmpty()
                                                        personId to loadedName
                                                    }
                                                }
                                            characterInfoDeferred?.await()?.let { (id, info) ->
                                                characterCardInfo[id] = info
                                            }
                                            actorNameDeferred?.await()?.let { (id, name) ->
                                                actorChineseNames[id] = name
                                            }
                                        }
                                    }
                                    val cardInfo = character.id?.let(characterCardInfo::get)
                                    val characterName = character.id?.let {
                                        cardInfo?.chineseName?.takeIf(String::isNotBlank)
                                            ?: characterChineseNames[it]?.takeIf(String::isNotBlank)
                                    }
                                        ?: character.name_cn?.takeIf { it.isNotBlank() }
                                        ?: character.name.orEmpty()
                                    val characterCardName = characterName
                                        .substringBefore('•')
                                        .substringBefore('·')
                                        .trim()
                                        .ifEmpty { characterName }
                                    val actorName = actor.id?.let {
                                        actorChineseNames[it]?.takeIf(String::isNotBlank)
                                    }
                                        ?: actor.name_cn?.takeIf { it.isNotBlank() }
                                        ?: actor.name.orEmpty()
                                    val onCharacterClick: (() -> Unit)? =
                                        character.id?.let { characterId ->
                                            {
                                                context.startActivity(
                                                    BangumiPersonDetailActivity.createCharacterIntent(
                                                        context,
                                                        characterId,
                                                        characterName
                                                    )
                                                )
                                            }
                                        }
                                    val actorClickModifier = actor.id?.let { personId ->
                                        Modifier.motionClickable {
                                            context.startActivity(
                                                BangumiPersonDetailActivity.createIntent(
                                                    context,
                                                    personId,
                                                    actorName
                                                )
                                            )
                                        }
                                    } ?: Modifier
                                    val characterNameGap = with(LocalDensity.current) {
                                        2.sp.toDp()
                                    }
                                    val actorNameGap = with(LocalDensity.current) {
                                        3.sp.toDp()
                                    }
                                    Column(
                                        modifier = Modifier
                                            .width(112.dp)
                                            .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                            .background(MiuixTheme.colorScheme.secondaryContainer)
                                            .padding(DesignTokens.SpaceMd),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 96.dp, height = 128.dp)
                                                .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                                                .background(MiuixTheme.colorScheme.secondaryContainer)
                                                .then(
                                                    onCharacterClick?.let {
                                                        Modifier.motionClickable(onClick = it)
                                                    } ?: Modifier
                                                )
                                        ) {
                                            val characterImage = character.images?.large
                                                ?: character.images?.medium
                                                ?: character.images?.small
                                                ?: character.images?.grid
                                            if (!characterImage.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(characterImage)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = characterName,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                    alignment = Alignment.TopCenter
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(characterNameGap))
                                        Text(
                                            text = characterCardName,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    onCharacterClick?.let {
                                                        Modifier.motionClickable(onClick = it)
                                                    } ?: Modifier
                                                )
                                                .padding(horizontal = DesignTokens.SpaceXs),
                                            fontSize = DesignTokens.TextBody2.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(actorNameGap))
                                        Text(
                                            text = actorName,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(actorClickModifier)
                                                .padding(horizontal = DesignTokens.SpaceXs),
                                            fontSize = DesignTokens.TextBody2.sp,
                                            color = if (actor.id != null) {
                                                DesignTokens.AccentBlue
                                            } else {
                                                dim
                                            },
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalScrollEdgeFades(
                                canScrollBackward = castListState.canScrollBackward,
                                canScrollForward = castListState.canScrollForward,
                                width = 18.dp
                            )
                            }
                        }

                        // 详情
                        if (detailRows.isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = stringResource(R.string.bangumi_details_title),
                                fontSize = DesignTokens.TextSubtitle.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            detailRows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = row.key,
                                        fontSize = 13.sp,
                                        color = dim,
                                        modifier = Modifier.width(100.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        row.values.forEach { value ->
                                            val isWebsite = isWebsiteInfoboxKey(row.key)
                                            val isAnimationCompany =
                                                isAnimationProductionKey(row.key)
                                            val displayValue = value.label?.let {
                                                "$it: ${value.text}"
                                            } ?: value.text
                                            val clickModifier = when {
                                                isAnimationCompany -> Modifier.combinedClickable(
                                                    onClickLabel = value.person?.id?.let {
                                                        value.text
                                                    },
                                                    onLongClickLabel = stringResource(
                                                        R.string.bangumi_copy_animation_company
                                                    ),
                                                    onLongClick = {
                                                        copyAnimationCompany(context, value.text)
                                                    },
                                                    onClick = {
                                                        value.person?.id?.let { personId ->
                                                            context.startActivity(
                                                                BangumiPersonDetailActivity.createIntent(
                                                                    context,
                                                                    personId,
                                                                    value.text
                                                                )
                                                            )
                                                        }
                                                    }
                                                )
                                                value.person?.id != null -> Modifier.motionClickable {
                                                    context.startActivity(
                                                        BangumiPersonDetailActivity.createIntent(
                                                            context,
                                                            value.person.id,
                                                            value.text
                                                        )
                                                    )
                                                }
                                                isWebsite -> Modifier.motionClickable {
                                                    openExternalWebLink(context, value.text)
                                                }
                                                else -> Modifier
                                            }
                                            Text(
                                                text = displayValue,
                                                fontSize = 13.sp,
                                                color = if (isWebsite || isAnimationCompany) {
                                                    DesignTokens.AccentBlue
                                                } else {
                                                    MiuixTheme.colorScheme.onSurface
                                                },
                                                modifier = clickModifier
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(40.dp))
                    }
                } else {
                    Box(
                        Modifier.fillMaxSize().padding(top = topBarHeightDp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.general_load_failed), color = dim)
                    }
                }
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .scrollLinkedTopBar(detailScrollState, topBarHeightDp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = statusBarDp + 4.dp,
                                end = 12.dp,
                                bottom = 4.dp
                            ),
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
                            text = displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = DesignTokens.TextHeadline.sp,
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
        }
        }

        if (hasCoverTransition && !coverTransitionFinished) {
            val sourceRect = requireNotNull(transitionSourceRect)
            val targetLeftPx = with(detailDensity) { DesignTokens.SpaceXl.toPx() }
            val targetTopPx = with(detailDensity) {
                (topBarHeightDp + DesignTokens.SpaceMd).toPx()
            }
            val targetWidthPx = with(detailDensity) { detailCoverWidth.toPx() }
            val targetHeightPx = with(detailDensity) { detailCoverHeight.toPx() }
            val startScaleX = sourceRect.width / targetWidthPx
            val startScaleY = sourceRect.height / targetHeightPx
            val transitionPainter = requireNotNull(initialCoverPainter)

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = targetLeftPx.roundToInt(),
                            y = targetTopPx.roundToInt()
                        )
                    }
                    .graphicsLayer {
                        val progress = coverTransitionProgress.value
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = (sourceRect.left - targetLeftPx) * (1f - progress)
                        translationY = (sourceRect.top - targetTopPx) * (1f - progress)
                        scaleX = startScaleX + (1f - startScaleX) * progress
                        scaleY = startScaleY + (1f - startScaleY) * progress
                    }
                    .size(detailCoverWidth, detailCoverHeight)
                    .clip(RoundedCornerShape(BANGUMI_GRID_COVER_CORNER))
                    .background(MiuixTheme.colorScheme.surfaceVariant)
            ) {
                Image(
                    painter = transitionPainter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        AnimatedVisibility(
            visible = isTagInputFocused &&
                tagInputRect.width > 0f &&
                (isMyTagsLoading || myTagSuggestions.isNotEmpty()),
            enter = dropdownPopupEnter(),
            exit = dropdownPopupExit()
        ) {
            val density = LocalDensity.current
            val screenHeightDp = with(density) {
                LocalWindowInfo.current.containerSize.height.toDp()
            }
            val inputLeftDp = with(density) { tagInputRect.left.toDp() }
            val inputTopDp = with(density) { tagInputRect.top.toDp() }
            val inputBottomDp = with(density) { tagInputRect.bottom.toDp() }
            val inputWidthDp = with(density) { tagInputRect.width.toDp() }
            val availableBelow = screenHeightDp - inputBottomDp
            val showAboveInput = availableBelow < 180.dp
            val popupMaxHeight = minOf(
                220.dp,
                if (showAboveInput) {
                    inputTopDp - DesignTokens.SpaceSm
                } else {
                    availableBelow - DesignTokens.SpaceSm
                }
            ).coerceAtLeast(80.dp)
            val selectedTagKeys = remember(draftTags.text) {
                draftTags.text
                    .split(Regex("[,，\\s]+"))
                    .map { it.trim().lowercase(Locale.ROOT) }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }

            Box(Modifier.fillMaxSize()) {
                val positionModifier = if (showAboveInput) {
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = inputLeftDp,
                            bottom = screenHeightDp - inputTopDp + DesignTokens.SpaceXs
                        )
                } else {
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = inputLeftDp,
                            top = inputBottomDp + DesignTokens.SpaceXs
                        )
                }
                Card(
                    modifier = positionModifier
                        .width(inputWidthDp)
                        .heightIn(max = popupMaxHeight)
                        .noRippleClickable { },
                    cornerRadius = DesignTokens.CornerLarge
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(DesignTokens.SpaceLg)
                    ) {
                        Text(
                            text = stringResource(R.string.bangumi_my_tags),
                            fontSize = DesignTokens.TextBody1.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(DesignTokens.SpaceMd))
                        if (isMyTagsLoading && myTagSuggestions.isEmpty()) {
                            LoadingSkeletonBlock(
                                Modifier
                                    .fillMaxWidth()
                                    .height(DesignTokens.ButtonHeight)
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    DesignTokens.SpaceSm
                                ),
                                verticalArrangement = Arrangement.spacedBy(
                                    DesignTokens.SpaceSm
                                )
                            ) {
                                myTagSuggestions.forEach { tag ->
                                    val selected = tag.lowercase(Locale.ROOT) in selectedTagKeys
                                    Box(
                                        modifier = Modifier
                                            .clip(
                                                RoundedCornerShape(DesignTokens.CornerMedium)
                                            )
                                            .background(
                                                MiuixTheme.colorScheme.secondaryContainer
                                            )
                                            .motionClickable {
                                                if (!selected) {
                                                    draftTags = insertTagAtSelection(
                                                        value = draftTags,
                                                        tag = tag
                                                    )
                                                    detailViewModel.updateTags(draftTags.text)
                                                }
                                            }
                                            .padding(
                                                horizontal = DesignTokens.SpaceLg,
                                                vertical = DesignTokens.SpaceSm
                                            )
                                    ) {
                                        Text(
                                            text = tag,
                                            fontSize = DesignTokens.TextBody2.sp,
                                            color = if (selected) {
                                                DesignTokens.AccentBlue
                                            } else {
                                                MiuixTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isStatusDropdownVisible,
            enter = fadeIn(
                animationSpec = tween(DesignTokens.FadeInDuration)
            ),
            exit = fadeOut(
                animationSpec = tween(DesignTokens.CollapseDuration)
            )
        ) {
            val density = LocalDensity.current
            val triggerBottomDp = with(density) {
                (statusDropdownRect.bottom / density.density).dp
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DesignTokens.ScrimDark)
                    .noRippleClickable { isStatusDropdownVisible = false },
                contentAlignment = Alignment.TopStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = triggerBottomDp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    val screenWidthDp = with(density) {
                        LocalWindowInfo.current.containerSize.width.toDp()
                    }
                    val triggerRightDp = with(density) {
                        (statusDropdownRect.right / density.density).dp
                    }
                    AnimatedVisibility(
                        visible = isStatusDropdownVisible,
                        enter = dropdownPopupEnter(),
                        exit = dropdownPopupExit()
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(end = maxOf(0.dp, screenWidthDp - triggerRightDp))
                                .wrapContentWidth(),
                            cornerRadius = DesignTokens.CornerLarge
                        ) {
                            Column(Modifier.width(IntrinsicSize.Max)) {
                                typeLabels.forEach { (apiType, label) ->
                                    val selected = apiType == draftType
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .motionClickable {
                                                detailViewModel.updateType(apiType)
                                                isStatusDropdownVisible = false
                                            }
                                            .padding(
                                                horizontal = DesignTokens.SpaceXl,
                                                vertical = DesignTokens.SpaceLg
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = DesignTokens.TextSubtitle.sp,
                                            fontWeight = if (selected) FontWeight.Bold
                                                else FontWeight.Normal,
                                            color = if (selected) MiuixTheme.colorScheme.primary
                                                else MiuixTheme.colorScheme.onSurface,
                                            softWrap = false
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Spacer(Modifier.width(DesignTokens.SpaceMassive))
                                        Image(
                                            imageVector = MiuixIcons.Basic.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(DesignTokens.IconMd),
                                            colorFilter = ColorFilter.tint(
                                                if (selected) MiuixTheme.colorScheme.primary
                                                else Color.Transparent
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        editingWatchRecord?.let { record ->
            Dialog(
                onDismissRequest = { editingWatchRecord = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Card(
                    modifier = Modifier
                        .motionDialogSurface()
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.SpaceXl)
                        .imePadding(),
                    cornerRadius = DesignTokens.CornerLarge
                ) {
                    Column(modifier = Modifier.padding(DesignTokens.SpaceXl)) {
                        Text(
                            text = stringResource(R.string.activity_anime_record_edit),
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = DesignTokens.TextSubtitle.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(DesignTokens.SpaceMd))
                        Text(
                            text = record.date,
                            color = dim,
                            fontSize = DesignTokens.TextBody1.sp
                        )
                        Spacer(Modifier.height(DesignTokens.SpaceLg))
                        TextField(
                            value = editingWatchAmount,
                            onValueChange = { value ->
                                if (value.matches(Regex("""\d{0,4}(\.\d{0,2})?"""))) {
                                    editingWatchAmount = value
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(R.string.activity_anime_record_amount),
                            useLabelAsPlaceholder = true,
                            singleLine = true
                        )
                        Spacer(Modifier.height(DesignTokens.SpaceSm))
                        Text(
                            text = stringResource(
                                R.string.activity_anime_record_zero_hint
                            ),
                            color = dim,
                            fontSize = DesignTokens.TextCaption.sp
                        )
                        Spacer(Modifier.height(DesignTokens.SpaceLg))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(DesignTokens.ButtonHeightSmall)
                                    .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                    .noRippleClickable { editingWatchRecord = null }
                                    .padding(horizontal = DesignTokens.SpaceXl),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    color = dim,
                                    fontSize = DesignTokens.TextBody1.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(DesignTokens.SpaceMd))
                            val canSave = editingWatchAmount.toDoubleOrNull() != null
                            Box(
                                modifier = Modifier
                                    .height(DesignTokens.ButtonHeightSmall)
                                    .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                    .background(
                                        buttonBgColor().copy(
                                            alpha = if (canSave) {
                                                1f
                                            } else {
                                                DesignTokens.OpacityDisabled
                                            }
                                        )
                                    )
                                    .then(
                                        if (canSave) {
                                            Modifier.noRippleClickable {
                                                val episodes =
                                                    editingWatchAmount.toDoubleOrNull()
                                                        ?: return@noRippleClickable
                                                coroutineScope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        ActivityStats.updateAnimeRecord(
                                                            context = context,
                                                            subjectId = subjectId,
                                                            date = record.date,
                                                            episodes = episodes
                                                        )
                                                    }
                                                    activityHistoryRevision++
                                                    editingWatchRecord = null
                                                }
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(horizontal = DesignTokens.SpaceXl),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.settings_save_profile
                                    ),
                                    color = Color.White,
                                    fontSize = DesignTokens.TextBody1.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyAnimationCompany(context: Context, companyName: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText(context.getString(R.string.bangumi_company), companyName)
    )
    Toast.makeText(
        context,
        R.string.bangumi_animation_company_copied,
        Toast.LENGTH_SHORT
    ).show()
}

private fun insertTagAtSelection(
    value: TextFieldValue,
    tag: String
): TextFieldValue {
    val insertedTag = tag.trim()
    if (insertedTag.isEmpty()) return value

    val selectionStart = minOf(value.selection.start, value.selection.end)
        .coerceIn(0, value.text.length)
    val selectionEnd = maxOf(value.selection.start, value.selection.end)
        .coerceIn(selectionStart, value.text.length)
    val prefix = value.text.substring(0, selectionStart)
    val suffix = value.text.substring(selectionEnd)
    val beforeTag = when {
        prefix.isEmpty() -> ""
        prefix.last() == ',' || prefix.last() == '，' -> " "
        prefix.last().isWhitespace() -> ""
        else -> ", "
    }
    val afterTag = when {
        suffix.isEmpty() -> ""
        suffix.first() == ',' || suffix.first() == '，' -> ""
        suffix.first().isWhitespace() -> ","
        else -> ", "
    }
    val updatedText = prefix + beforeTag + insertedTag + afterTag + suffix
    val updatedCursor = prefix.length + beforeTag.length + insertedTag.length
    return TextFieldValue(
        text = updatedText,
        selection = TextRange(updatedCursor)
    )
}

@Composable
private fun ProgressSectionHeader(
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    ExpandableSectionTrigger(
        expanded = expanded,
        onToggle = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .height(DesignTokens.ButtonHeight)
            .clip(RoundedCornerShape(DesignTokens.CornerMedium)),
        arrowColor = MiuixTheme.colorScheme.onSurface.copy(
            alpha = DesignTokens.OpacityBody
        )
    ) {
        Text(
            text = stringResource(R.string.bangumi_my_collection),
            fontSize = DesignTokens.TextSubtitle.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BangumiCollectionLoadingSkeleton() {
    Column(
        modifier = Modifier.padding(
            start = DesignTokens.SpaceXl,
            end = DesignTokens.SpaceXl,
            top = DesignTokens.SpaceXxl / 4f,
            bottom = DesignTokens.SpaceXl
        ),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.SpaceXxl / 4f)
    ) {
        LoadingSkeletonBlock(Modifier.fillMaxWidth().height(DesignTokens.IconXl))
        LoadingSkeletonBlock(Modifier.fillMaxWidth().height(DesignTokens.ButtonHeight))
        LoadingSkeletonBlock(Modifier.fillMaxWidth().height(DesignTokens.ButtonHeight))
    }
}

@Composable
private fun SoftRatingStar(
    selected: Boolean,
    selectedColor: Color
) {
    val outlineColor = MiuixTheme.colorScheme.onSurface.copy(
        alpha = DesignTokens.OpacityDisabled
    )
    Canvas(Modifier.size(18.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension * 0.43f
        val innerRadius = outerRadius * 0.5f
        val points = List(10) { index ->
            val radius = if (index % 2 == 0) outerRadius else innerRadius
            val angle = -PI / 2 + index * PI / 5
            Offset(
                x = center.x + cos(angle).toFloat() * radius,
                y = center.y + sin(angle).toFloat() * radius
            )
        }
        val cornerFraction = 0.2f
        val starPath = Path()
        points.forEachIndexed { index, point ->
            val previous = points[(index - 1 + points.size) % points.size]
            val next = points[(index + 1) % points.size]
            val beforeCorner = Offset(
                x = point.x + (previous.x - point.x) * cornerFraction,
                y = point.y + (previous.y - point.y) * cornerFraction
            )
            val afterCorner = Offset(
                x = point.x + (next.x - point.x) * cornerFraction,
                y = point.y + (next.y - point.y) * cornerFraction
            )
            if (index == 0) {
                starPath.moveTo(beforeCorner.x, beforeCorner.y)
            } else {
                starPath.lineTo(beforeCorner.x, beforeCorner.y)
            }
            starPath.quadraticTo(
                point.x,
                point.y,
                afterCorner.x,
                afterCorner.y
            )
        }
        starPath.close()

        if (selected) {
            drawPath(starPath, selectedColor.copy(alpha = 0.9f))
        } else {
            drawPath(
                path = starPath,
                color = outlineColor,
                style = Stroke(
                    width = 1.25.dp.toPx(),
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
