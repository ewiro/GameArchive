package com.example.gamearchive

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.*
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class BangumiDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val subjectId = intent.getIntExtra("SUBJECT_ID", 0)
        val subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        val subjectNameCn = intent.getStringExtra("SUBJECT_NAME_CN") ?: ""
        val subjectImage = intent.getStringExtra("SUBJECT_IMAGE") ?: ""

        setContent {
            MiuixThemeForApp {
                BangumiDetailScreen(
                    subjectId = subjectId,
                    subjectName = subjectName,
                    subjectNameCn = subjectNameCn,
                    subjectImage = subjectImage,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun BangumiDetailScreen(
    subjectId: Int,
    subjectName: String,
    subjectNameCn: String,
    subjectImage: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val statusBarDp = statusBarHeightDp()

    var detail by remember { mutableStateOf<BangumiSubjectDetail?>(null) }
    var subjectPersons by remember { mutableStateOf<List<BangumiPerson>>(emptyList()) }
    var myCollection by remember { mutableStateOf<BangumiMyCollection?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isCollectionLoaded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var draftType by remember { mutableIntStateOf(0) }
    var draftRate by remember { mutableIntStateOf(0) }
    var draftTags by remember { mutableStateOf("") }
    var draftComment by remember { mutableStateOf("") }
    var mainEpisodeCount by remember { mutableStateOf<Int?>(null) }
    var mainEpisodes by remember { mutableStateOf<List<BangumiUserEpisodeCollection>>(emptyList()) }
    var isEpisodeProgressUnavailable by remember { mutableStateOf(false) }
    var savedEpisodeProgress by remember { mutableIntStateOf(0) }
    var savedRegularEpisodeProgress by remember { mutableIntStateOf(0) }
    var draftEpisodeProgress by remember { mutableIntStateOf(0) }
    var isStatusDropdownVisible by remember { mutableStateOf(false) }
    var statusDropdownRect by remember { mutableStateOf(Rect.Zero) }
    var isProgressExpanded by remember { mutableStateOf(false) }
    var activityHistoryRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(subjectId) {
        val (loadedDetail, loadedPersons, loadedEpisodePage) = coroutineScope {
            val detailDeferred = async {
                runCatching { GameArchiveApp.bgmService.getSubject(subjectId) }.getOrNull()
            }
            val personsDeferred = async {
                runCatching {
                    GameArchiveApp.bgmService.getSubjectPersons(subjectId)
                }.getOrDefault(emptyList())
            }
            val episodesDeferred = async {
                runCatching {
                    val firstPage = GameArchiveApp.bgmService
                        .getSubjectEpisodes(subjectId, limit = 100)
                    if (firstPage.total <= firstPage.data.orEmpty().size) {
                        firstPage
                    } else {
                        val episodes = firstPage.data.orEmpty().toMutableList()
                        var offset = episodes.size
                        while (offset < firstPage.total) {
                            val page = GameArchiveApp.bgmService.getSubjectEpisodes(
                                subjectId = subjectId,
                                limit = 100,
                                offset = offset
                            )
                            val newEpisodes = page.data.orEmpty()
                            if (newEpisodes.isEmpty()) break
                            episodes += newEpisodes
                            offset += newEpisodes.size
                        }
                        firstPage.copy(data = episodes)
                    }
                }.getOrNull()
            }
            Triple(
                detailDeferred.await(),
                personsDeferred.await(),
                episodesDeferred.await()
            )
        }
        detail = loadedDetail
        subjectPersons = loadedPersons
        mainEpisodeCount = loadedEpisodePage?.total?.takeIf { it > 0 }
        mainEpisodes = loadedEpisodePage?.data.orEmpty()
            .sortedWith(compareBy(
                { it.ep ?: Double.MAX_VALUE },
                { it.sort ?: Double.MAX_VALUE }
            ))
            .map { BangumiUserEpisodeCollection(episode = it, type = 0) }
        if (loadedDetail == null) {
            Toast.makeText(context, context.getString(R.string.bangumi_detail_load_failed), Toast.LENGTH_SHORT).show()
        }
        val token = UserPrefs.getBangumiAccessToken(context)
        if (token.isNotEmpty()) {
            val service = GameArchiveApp.createAuthenticatedBgmService(token)
            var username = UserPrefs.getBangumiUsername(context)
            if (username.isBlank()) {
                username = runCatching { service.getCurrentUser().username }.getOrDefault("")
                if (username.isNotBlank()) {
                    UserPrefs.setBangumiUsername(context, username)
                }
            }
            val collectionResult = runCatching {
                service.getMyCollection(username, subjectId)
            }
            val collection = collectionResult.getOrNull()
            if (collection != null) {
                myCollection = collection
                draftType = collection.type ?: 1
                draftRate = collection.rate ?: 0
                draftTags = collection.tags.orEmpty().joinToString(", ")
                draftComment = collection.comment.orEmpty()
                savedEpisodeProgress = collection.ep_status ?: 0
                savedRegularEpisodeProgress = savedEpisodeProgress
                draftEpisodeProgress = savedEpisodeProgress
                val loadedEpisodes = runCatching {
                    service.getEpisodeCollections(subjectId).data.orEmpty()
                }.getOrNull()
                if (loadedEpisodes != null) {
                    mainEpisodes = loadedEpisodes
                        .sortedWith(compareBy(
                            { it.episode.ep ?: Double.MAX_VALUE },
                            { it.episode.sort ?: Double.MAX_VALUE }
                        ))
                    savedRegularEpisodeProgress = mainEpisodes.count { it.type == 2 }
                    savedEpisodeProgress = savedRegularEpisodeProgress
                    draftEpisodeProgress = savedRegularEpisodeProgress
                } else {
                    isEpisodeProgressUnavailable = true
                }
            } else {
                val error = collectionResult.exceptionOrNull()
                if ((error as? HttpException)?.code() != 404) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.bangumi_collection_load_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        isCollectionLoaded = true
        isLoading = false
    }

    val coroutineScope = rememberCoroutineScope()

    fun saveCollection() {
        val token = UserPrefs.getBangumiAccessToken(context)
        if (token.isEmpty() || isSaving || draftType == 0) return
        val tags = draftTags
            .split(Regex("[,，\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        val previousApiType = myCollection?.type ?: 0
        val targetApiType = draftType
        val previousRegularProgress = savedRegularEpisodeProgress
        isSaving = true
        coroutineScope.launch {
            try {
                val service = GameArchiveApp.createAuthenticatedBgmService(token)
                val response = service.updateCollection(
                    subjectId,
                    BangumiCollectionUpdate(
                        type = targetApiType,
                        rate = draftRate,
                        comment = draftComment.trim(),
                        tags = tags,
                        `private` = myCollection?.private ?: false
                    )
                )
                if (!response.isSuccessful) throw HttpException(response)

                val boundedEpisodeProgress = draftEpisodeProgress.coerceIn(0, mainEpisodes.size)
                val boundedSavedProgress = savedEpisodeProgress.coerceIn(0, mainEpisodes.size)
                val changedEpisodes = when {
                    boundedEpisodeProgress > boundedSavedProgress ->
                        mainEpisodes.subList(boundedSavedProgress, boundedEpisodeProgress) to 2
                    boundedEpisodeProgress < boundedSavedProgress ->
                        mainEpisodes.subList(boundedEpisodeProgress, boundedSavedProgress) to 0
                    else -> emptyList<BangumiUserEpisodeCollection>() to 0
                }
                if (changedEpisodes.first.isNotEmpty()) {
                    val episodeResponse = service.updateEpisodeCollections(
                        subjectId,
                        BangumiEpisodeCollectionUpdate(
                            episode_id = changedEpisodes.first.map { it.episode.id },
                            type = changedEpisodes.second
                        )
                    )
                    if (!episodeResponse.isSuccessful) throw HttpException(episodeResponse)
                }
                val changedEpisodeIds = changedEpisodes.first
                    .mapTo(hashSetOf()) { it.episode.id }
                val currentWatchedEpisodeIds = mainEpisodes.mapNotNull { episode ->
                    val type = if (episode.episode.id in changedEpisodeIds) {
                        changedEpisodes.second
                    } else {
                        episode.type
                    }
                    episode.episode.id.takeIf { type == 2 }
                }

                var username = UserPrefs.getBangumiUsername(context)
                if (username.isBlank()) {
                    username = service.getCurrentUser().username
                    UserPrefs.setBangumiUsername(context, username)
                }
                myCollection = service.getMyCollection(username, subjectId)
                withContext(Dispatchers.IO) {
                    ActivityStats.recordBangumiSave(
                        context = context,
                        subjectId = subjectId,
                        title = detail?.name ?: subjectName,
                        secondaryTitle = detail?.name_cn ?: subjectNameCn,
                        imageUrl = detail?.images?.large
                            ?: detail?.images?.common
                            ?: subjectImage,
                        previousApiType = previousApiType,
                        currentApiType = targetApiType,
                        previousEpisodes = previousRegularProgress,
                        currentEpisodes = boundedEpisodeProgress,
                        currentEpisodeIds = currentWatchedEpisodeIds
                    )
                }
                activityHistoryRevision++
                savedRegularEpisodeProgress = boundedEpisodeProgress
                savedEpisodeProgress = boundedEpisodeProgress
                draftEpisodeProgress = savedEpisodeProgress
                BangumiViewModel.collectionChanged = true
                Toast.makeText(
                    context,
                    context.getString(R.string.bangumi_collection_saved),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                val messageRes = if ((e as? HttpException)?.code() == 401) {
                    R.string.bangumi_authorization_expired
                } else {
                    R.string.bangumi_collection_save_failed
                }
                Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
            }
            isSaving = false
        }
    }

    val displayName = subjectNameCn.ifEmpty { subjectName }
    val score = extractScore(detail?.rating)
    val ratingMode = UserPrefs.getBangumiRatingMode(context)
    val watchRecords by produceState<List<ItemActivityRecord>>(
        initialValue = emptyList(),
        subjectId,
        activityHistoryRevision
    ) {
        value = withContext(Dispatchers.IO) {
            ActivityStats.getAnimeRecords(context, subjectId)
        }
    }
    val showRating = ratingMode == 0  // 0=展示评分, 1=仅我的评分(详情页无), 2=不展示
    val dim = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
    val typeLabels = listOf(
        1 to context.getString(R.string.bangumi_wish),
        3 to context.getString(R.string.bangumi_doing),
        2 to context.getString(R.string.bangumi_done),
        4 to context.getString(R.string.bangumi_on_hold),
        5 to context.getString(R.string.bangumi_dropped)
    )

    Box(Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶栏
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = statusBarDp + 4.dp, end = 12.dp, bottom = 4.dp),
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
                        text = displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = DesignTokens.TextHeadline.sp,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }

                if (isLoading) {
                    AnimeDetailLoadingSkeleton()
                } else if (detail != null) {
                    val d = detail!!
                    val chapterCount = mainEpisodes.size.takeIf { it > 0 }
                        ?: mainEpisodeCount
                        ?: d.eps?.takeIf { it > 0 }
                    val detailRows = remember(d, subjectPersons, chapterCount, context) {
                        val infoboxRows = d.infobox.orEmpty().mapNotNull { item ->
                            val value = when (item.value) {
                                is String -> item.value
                                is Number -> item.value.toString()
                                else -> ""
                            }.trim()
                            if (value.isEmpty()) null else item.key.trim() to value
                        }.toMutableList()
                        if (chapterCount != null) {
                            val episodeKeys = setOf("话数", "話数", "集数", "episode", "episodes")
                            val episodeValue = context.getString(
                                R.string.bangumi_card_episode_count,
                                chapterCount
                            )
                            val episodeIndex = infoboxRows.indexOfFirst {
                                it.first.lowercase() in episodeKeys
                            }
                            if (episodeIndex >= 0) {
                                infoboxRows[episodeIndex] =
                                    infoboxRows[episodeIndex].first to episodeValue
                            } else {
                                infoboxRows.add(
                                    context.getString(R.string.bangumi_detail_episode_count_label) to
                                        episodeValue
                                )
                            }
                        }
                        val existingKeys = infoboxRows
                            .map { it.first.lowercase() }
                            .toSet()
                        val personRows = subjectPersons
                            .asSequence()
                            .filter { !it.relation.isNullOrBlank() && !it.name.isNullOrBlank() }
                            .groupBy { it.relation!!.trim() }
                            .mapNotNull { (relation, persons) ->
                                if (relation.lowercase() in existingKeys) {
                                    null
                                } else {
                                    val names = persons
                                        .mapNotNull { it.name?.trim() }
                                        .filter { it.isNotEmpty() }
                                        .distinct()
                                    if (names.isEmpty()) null else relation to names.joinToString("、")
                                }
                            }
                        infoboxRows + personRows
                    }
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // 封面 + 基本信息
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // 封面
                            Box(
                                modifier = Modifier
                                    .size(width = 120.dp, height = 168.dp)
                                    .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                            ) {
                                val coverUrl = d.images?.large ?: d.images?.common ?: subjectImage
                                if (coverUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(coverUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            // 信息
                            Column(modifier = Modifier.weight(1f).height(168.dp)) {
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

                                // 评分
                                if (showRating && score != null && score > 0) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = String.format("%.1f", score),
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
                                    Spacer(Modifier.height(4.dp))
                                }

                                Spacer(Modifier.weight(1f))

                                // 日期 + 话数（置底，优先使用章节列表数量）
                                if (chapterCount != null) {
                                    Text(
                                        text = context.getString(
                                            R.string.bangumi_episode_total,
                                            chapterCount
                                        ),
                                        fontSize = DesignTokens.TextBody1.sp,
                                        color = dim
                                    )
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
                        if (authorized && isCollectionLoaded) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ProgressSectionHeader(
                                    expanded = isProgressExpanded,
                                    onToggle = { isProgressExpanded = !isProgressExpanded }
                                )
                                AnimatedVisibility(
                                    visible = isProgressExpanded,
                                    enter = smoothExpandEnter(),
                                    exit = smoothExpandExit()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(
                                            start = DesignTokens.SpaceXl,
                                            end = DesignTokens.SpaceXl,
                                            bottom = DesignTokens.SpaceXl
                                        )
                                    ) {
                                    Spacer(Modifier.height(DesignTokens.SpaceLg))

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
                                            .padding(vertical = DesignTokens.SpaceLg),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = context.getString(R.string.bangumi_collection_status),
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
                                                text = context.getString(R.string.bangumi_episode_progress),
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
                                                                contentDescription = context.getString(
                                                                    R.string.bangumi_episode_decrease
                                                                )
                                                            }
                                                            .noRippleClickable {
                                                                if (canDecrease) draftEpisodeProgress--
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
                                                        text = context.getString(
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
                                                            contentDescription = context.getString(
                                                                R.string.bangumi_episode_increase
                                                            )
                                                        }
                                                        .noRippleClickable {
                                                            if (canIncrease) draftEpisodeProgress++
                                                        }
                                                )
                                            }
                                        }
                                    } else if (isEpisodeProgressUnavailable) {
                                        Spacer(Modifier.height(DesignTokens.SpaceXxl / 4f))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(DesignTokens.ButtonHeight),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = context.getString(R.string.bangumi_episode_progress),
                                                fontSize = DesignTokens.TextBody1.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MiuixTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = context.getString(
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
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = context.getString(R.string.bangumi_my_rating),
                                            fontSize = DesignTokens.TextBody1.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.onSurface
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
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .semantics {
                                                            contentDescription = context.getString(
                                                                R.string.bangumi_rating_value,
                                                                i
                                                            )
                                                        }
                                                        .noRippleClickable {
                                                            draftRate = if (draftRate == i) 0 else i
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    SoftRatingStar(
                                                        selected = selected,
                                                        selectedColor = selectedRatingColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(DesignTokens.SpaceXxl))

                                    TextField(
                                        value = draftTags,
                                        onValueChange = { draftTags = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = context.getString(R.string.bangumi_tags_hint),
                                        useLabelAsPlaceholder = true,
                                        singleLine = true
                                    )
                                    Spacer(Modifier.height(DesignTokens.SpaceLg))

                                    TextField(
                                        value = draftComment,
                                        onValueChange = { draftComment = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = context.getString(R.string.bangumi_comment_hint),
                                        useLabelAsPlaceholder = true,
                                        singleLine = false
                                    )
                                    Spacer(Modifier.height(DesignTokens.SpaceXxl))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(DesignTokens.ButtonHeight)
                                            .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                            .background(
                                                if (isSaving || draftType == 0) {
                                                    buttonBgColor().copy(alpha = DesignTokens.OpacityDisabled)
                                                } else {
                                                    buttonBgColor()
                                                }
                                            )
                                            .noRippleClickable {
                                                if (!isSaving && draftType != 0) saveCollection()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = context.getString(
                                                if (isSaving) R.string.bangumi_saving
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
                            Spacer(Modifier.height(DesignTokens.SpaceMd))
                        } else if (!authorized) {
                            Text(
                                text = context.getString(R.string.bangumi_authorization_required),
                                fontSize = DesignTokens.TextBody1.sp,
                                color = dim
                            )
                            Spacer(Modifier.height(DesignTokens.SpaceXxl))
                        }

                        // 标签
                        ActivityHistorySection(
                            kind = ActivityKind.ANIME,
                            records = watchRecords,
                            titleFontSize = DesignTokens.TextSubtitle.sp
                        )
                        Spacer(Modifier.height(DesignTokens.SpaceXxl))

                        if (!d.tags.isNullOrEmpty()) {
                            Text(
                                text = context.getString(R.string.bangumi_tags_title),
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
                                text = context.getString(R.string.bangumi_summary_title),
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

                        // 详情
                        if (detailRows.isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = context.getString(R.string.bangumi_details_title),
                                fontSize = DesignTokens.TextSubtitle.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            detailRows.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = key,
                                        fontSize = 13.sp,
                                        color = dim,
                                        modifier = Modifier.width(100.dp)
                                    )
                                    Text(
                                        text = value,
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(40.dp))
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(context.getString(R.string.general_load_failed), color = dim)
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
                        enter = smoothExpandEnter(),
                        exit = smoothExpandExit()
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
                                            .noRippleClickable {
                                                draftType = apiType
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
    }
}

@Composable
private fun ProgressSectionHeader(
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DesignTokens.ButtonHeight)
            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
            .semantics {
                contentDescription = context.getString(
                    if (expanded) R.string.bangumi_progress_collapse
                    else R.string.bangumi_progress_expand
                )
            }
            .noRippleClickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = context.getString(R.string.bangumi_my_collection),
            fontSize = DesignTokens.TextSubtitle.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        ExpandableArrow(
            expanded = expanded,
            color = MiuixTheme.colorScheme.onSurface.copy(
                alpha = DesignTokens.OpacityBody
            )
        )
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
            starPath.quadraticBezierTo(
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

private fun extractScore(r: Any?): Double? = when (r) {
    is Map<*, *> -> {
        val s = r["score"]
        when (s) { is Double -> s; is Number -> s.toDouble(); else -> null }
    }
    else -> null
}

private fun bangumiScoreColor(score: Double): Color = when {
    score >= 8.0 -> Color(0xFFE74C3C)
    score >= 7.0 -> Color(0xFFE67E22)
    score >= 5.0 -> Color(0xFF3498DB)
    else -> Color(0xFF999999)
}

private fun bangumiGrade(score: Double): String = when {
    score >= 9.0 -> "超神作"
    score >= 8.5 -> "神作"
    score >= 8.0 -> "力荐"
    score >= 7.5 -> "推荐"
    score >= 7.0 -> "不错"
    score >= 6.0 -> "还行"
    score >= 5.0 -> "一般"
    else -> "较差"
}
