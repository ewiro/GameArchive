package com.example.gamearchive

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import retrofit2.HttpException
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.*
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    var myCollection by remember { mutableStateOf<BangumiMyCollection?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isCollectionLoaded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var draftType by remember { mutableIntStateOf(1) }
    var draftRate by remember { mutableIntStateOf(0) }
    var draftTags by remember { mutableStateOf("") }
    var draftComment by remember { mutableStateOf("") }
    var mainEpisodes by remember { mutableStateOf<List<BangumiUserEpisodeCollection>>(emptyList()) }
    var isEpisodeProgressUnavailable by remember { mutableStateOf(false) }
    var savedEpisodeProgress by remember { mutableIntStateOf(0) }
    var draftEpisodeProgress by remember { mutableIntStateOf(0) }

    LaunchedEffect(subjectId) {
        try {
            detail = GameArchiveApp.bgmService.getSubject(subjectId)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.bangumi_detail_load_failed), Toast.LENGTH_SHORT).show()
        }
        val token = UserPrefs.getBangumiAccessToken(context)
        if (token.isNotEmpty()) {
            try {
                val service = GameArchiveApp.createAuthenticatedBgmService(token)
                var username = UserPrefs.getBangumiUsername(context)
                if (username.isBlank()) {
                    username = service.getCurrentUser().username
                    UserPrefs.setBangumiUsername(context, username)
                }
                val collection = service.getMyCollection(username, subjectId)
                myCollection = collection
                draftType = collection.type ?: 1
                draftRate = collection.rate ?: 0
                draftTags = collection.tags.orEmpty().joinToString(", ")
                draftComment = collection.comment.orEmpty()
                savedEpisodeProgress = collection.ep_status ?: 0
                draftEpisodeProgress = savedEpisodeProgress
                try {
                    mainEpisodes = service.getEpisodeCollections(subjectId).data.orEmpty()
                        .sortedWith(compareBy(
                            { it.episode.ep ?: Double.MAX_VALUE },
                            { it.episode.sort ?: Double.MAX_VALUE }
                        ))
                } catch (_: Exception) {
                    isEpisodeProgressUnavailable = true
                }
            } catch (e: Exception) {
                if ((e as? HttpException)?.code() != 404) {
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
        if (token.isEmpty() || isSaving) return
        val tags = draftTags
            .split(Regex("[,，\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        isSaving = true
        coroutineScope.launch {
            try {
                val service = GameArchiveApp.createAuthenticatedBgmService(token)
                val response = service.updateCollection(
                    subjectId,
                    BangumiCollectionUpdate(
                        type = draftType,
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

                var username = UserPrefs.getBangumiUsername(context)
                if (username.isBlank()) {
                    username = service.getCurrentUser().username
                    UserPrefs.setBangumiUsername(context, username)
                }
                myCollection = service.getMyCollection(username, subjectId)
                savedEpisodeProgress = myCollection?.ep_status ?: boundedEpisodeProgress
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
    val showRating = ratingMode == 0  // 0=展示评分, 1=仅我的评分(详情页无), 2=不展示
    val dim = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)

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
                            contentDescription = "Back",
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载中...", color = dim)
                    }
                } else if (detail != null) {
                    val d = detail!!
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

                                // 日期 + 话数（置底，用eps正集数不含番外）
                                if (d.eps != null && d.eps!! > 0) {
                                    Text(text = "共 ${d.eps} 话", fontSize = DesignTokens.TextBody1.sp, color = dim)
                                } else {
                                    Text(text = "连载中", fontSize = DesignTokens.TextBody1.sp, color = dim)
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
                            val typeLabels = listOf(
                                1 to context.getString(R.string.bangumi_wish),
                                3 to context.getString(R.string.bangumi_doing),
                                2 to context.getString(R.string.bangumi_done),
                                4 to context.getString(R.string.bangumi_on_hold),
                                5 to context.getString(R.string.bangumi_dropped)
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = DesignTokens.CornerLarge
                            ) {
                                Column(modifier = Modifier.padding(DesignTokens.SpaceXl)) {
                                    Text(
                                        text = context.getString(R.string.bangumi_my_collection),
                                        fontSize = DesignTokens.TextTitle.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = context.getString(R.string.bangumi_collection_sync_hint),
                                        fontSize = DesignTokens.TextBody2.sp,
                                        color = dim
                                    )
                                    Spacer(Modifier.height(DesignTokens.SpaceXxl))

                                    Text(
                                        text = context.getString(R.string.bangumi_collection_status),
                                        fontSize = DesignTokens.TextBody1.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(DesignTokens.SpaceMd))
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpaceMd),
                                        verticalArrangement = Arrangement.spacedBy(DesignTokens.SpaceMd)
                                    ) {
                                        typeLabels.forEach { (apiType, label) ->
                                            val selected = apiType == draftType
                                            Box(
                                                modifier = Modifier
                                                    .height(DesignTokens.ButtonHeight)
                                                    .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                                    .background(
                                                        if (selected) MiuixTheme.colorScheme.primary
                                                        else MiuixTheme.colorScheme.secondaryContainer
                                                    )
                                                    .noRippleClickable { draftType = apiType }
                                                    .padding(horizontal = DesignTokens.SpaceXl),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = DesignTokens.TextBody1.sp,
                                                    color = if (selected) Color.White
                                                        else MiuixTheme.colorScheme.onSurface,
                                                    fontWeight = if (selected) FontWeight.Bold
                                                        else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }

                                    if (mainEpisodes.isNotEmpty()) {
                                        Spacer(Modifier.height(DesignTokens.SpaceXxl))
                                        Text(
                                            text = context.getString(R.string.bangumi_episode_progress),
                                            fontSize = DesignTokens.TextBody1.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(DesignTokens.SpaceMd))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                                .background(MiuixTheme.colorScheme.secondaryContainer)
                                                .padding(DesignTokens.SpaceLg)
                                        ) {
                                            Column {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    val canDecrease = draftEpisodeProgress > 0
                                                    Box(
                                                        modifier = Modifier
                                                            .size(DesignTokens.ButtonHeight)
                                                            .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                                            .background(
                                                                MiuixTheme.colorScheme.surface.copy(
                                                                    alpha = if (canDecrease) 1f
                                                                    else DesignTokens.OpacityDisabled
                                                                )
                                                            )
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
                                                            color = MiuixTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            text = context.getString(
                                                                R.string.bangumi_episode_seen,
                                                                draftEpisodeProgress
                                                            ),
                                                            fontSize = DesignTokens.TextSubtitle.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MiuixTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = context.getString(
                                                                R.string.bangumi_episode_total,
                                                                mainEpisodes.size
                                                            ),
                                                            fontSize = DesignTokens.TextBody2.sp,
                                                            color = dim
                                                        )
                                                    }
                                                    val canIncrease = draftEpisodeProgress < mainEpisodes.size
                                                    Box(
                                                        modifier = Modifier
                                                            .size(DesignTokens.ButtonHeight)
                                                            .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                                            .background(
                                                                MiuixTheme.colorScheme.surface.copy(
                                                                    alpha = if (canIncrease) 1f
                                                                    else DesignTokens.OpacityDisabled
                                                                )
                                                            )
                                                            .semantics {
                                                                contentDescription = context.getString(
                                                                    R.string.bangumi_episode_increase
                                                                )
                                                            }
                                                            .noRippleClickable {
                                                                if (canIncrease) draftEpisodeProgress++
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "+",
                                                            fontSize = DesignTokens.TextHeadline.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MiuixTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(DesignTokens.SpaceLg))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(DesignTokens.SpaceSm)
                                                        .clip(RoundedCornerShape(DesignTokens.CornerSmall))
                                                        .background(MiuixTheme.colorScheme.surface)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(
                                                                draftEpisodeProgress.toFloat() /
                                                                    mainEpisodes.size.toFloat()
                                                            )
                                                            .fillMaxHeight()
                                                            .background(MiuixTheme.colorScheme.primary)
                                                    )
                                                }
                                            }
                                        }
                                    } else if (isEpisodeProgressUnavailable) {
                                        Spacer(Modifier.height(DesignTokens.SpaceXxl))
                                        Text(
                                            text = context.getString(R.string.bangumi_episode_progress),
                                            fontSize = DesignTokens.TextBody1.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = context.getString(
                                                R.string.bangumi_episode_progress_unavailable
                                            ),
                                            fontSize = DesignTokens.TextBody2.sp,
                                            color = dim
                                        )
                                    }

                                    Spacer(Modifier.height(DesignTokens.SpaceXxl))
                                    Text(
                                        text = context.getString(R.string.bangumi_my_rating),
                                        fontSize = DesignTokens.TextBody1.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(DesignTokens.SpaceMd))
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpaceMd),
                                        verticalArrangement = Arrangement.spacedBy(DesignTokens.SpaceMd)
                                    ) {
                                        for (i in 0..10) {
                                            val label = if (i == 0) "—" else "$i"
                                            val selected = i == draftRate
                                            Box(
                                                modifier = Modifier
                                                    .size(DesignTokens.ButtonHeight)
                                                    .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                                                    .background(
                                                        if (selected) MiuixTheme.colorScheme.primary
                                                        else MiuixTheme.colorScheme.secondaryContainer
                                                    )
                                                    .noRippleClickable { draftRate = i },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = DesignTokens.TextBody1.sp,
                                                    color = if (selected) Color.White
                                                        else MiuixTheme.colorScheme.onSurface,
                                                    fontWeight = if (selected) FontWeight.Bold
                                                        else FontWeight.Normal
                                                )
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
                                                if (isSaving) {
                                                    buttonBgColor().copy(alpha = DesignTokens.OpacityDisabled)
                                                } else {
                                                    buttonBgColor()
                                                }
                                            )
                                            .noRippleClickable {
                                                if (!isSaving) saveCollection()
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
                            Spacer(Modifier.height(DesignTokens.SpaceXxl))
                        } else if (!authorized) {
                            Text(
                                text = context.getString(R.string.bangumi_authorization_required),
                                fontSize = DesignTokens.TextBody1.sp,
                                color = dim
                            )
                            Spacer(Modifier.height(DesignTokens.SpaceXxl))
                        }

                        // 标签
                        if (!d.tags.isNullOrEmpty()) {
                            Text(
                                text = "标签",
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
                                d.tags!!.forEach { tag ->
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
                                text = "简介",
                                fontSize = DesignTokens.TextSubtitle.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            // WebView 渲染简介
                            val isDarkWebView = isSystemInDarkTheme()
                            AndroidView(
                                factory = { ctx ->
                                    android.webkit.WebView(ctx).apply {
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                                        )
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        isVerticalScrollBarEnabled = false
                                        settings.javaScriptEnabled = false
                                        val cssText = if (isDarkWebView) "#CCCCCC" else "#333333"
                                        val summaryHtml = d.summary
                                            ?.replace("\r\n", "\n")
                                            ?.split("\n")
                                            ?.filter { it.isNotBlank() }
                                            ?.joinToString("") { "<p style=\"text-indent:1em;margin:0.5em 0\">$it</p>" }
                                            ?: ""
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
                        if (!d.infobox.isNullOrEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "详情",
                                fontSize = DesignTokens.TextSubtitle.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            d.infobox!!.forEach { item ->
                                val v = when (item.value) {
                                    is String -> item.value as String
                                    is Number -> item.value.toString()
                                    else -> ""
                                }
                                if (v.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = item.key,
                                            fontSize = 13.sp,
                                            color = dim,
                                            modifier = Modifier.width(72.dp)
                                        )
                                        Text(
                                            text = v,
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(40.dp))
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载失败", color = dim)
                    }
                }
            }
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
