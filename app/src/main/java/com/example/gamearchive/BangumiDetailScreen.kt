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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.CancellationException
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
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val detailViewModel: BangumiDetailViewModel = viewModel()
    val detailUiState by detailViewModel.uiState.collectAsStateWithLifecycle()
    val detailLoadFailedText = stringResource(R.string.bangumi_detail_load_failed)
    val collectionLoadFailedText = stringResource(R.string.bangumi_collection_load_failed)
    val authorizationExpiredText = stringResource(R.string.bangumi_authorization_expired)
    val collectionSavedText = stringResource(R.string.bangumi_collection_saved)
    val collectionSaveFailedText = stringResource(R.string.bangumi_collection_save_failed)
    val episodeDecreaseDescription = stringResource(R.string.bangumi_episode_decrease)
    val episodeIncreaseDescription = stringResource(R.string.bangumi_episode_increase)
    val statusBarDp = statusBarHeightDp()
    val sequelCoverGap = DesignTokens.SpaceXs + with(LocalDensity.current) {
        DesignTokens.TextBody1.sp.toDp()
    }

    var detail by remember { mutableStateOf<BangumiSubjectDetail?>(null) }
    var subjectPersons by remember { mutableStateOf<List<BangumiPerson>>(emptyList()) }
    var subjectCharacters by remember { mutableStateOf<List<BangumiRelatedCharacter>>(emptyList()) }
    var sequelSubjects by remember { mutableStateOf<List<BangumiRelatedSubject>>(emptyList()) }
    val characterChineseNames = remember { mutableStateMapOf<Int, String>() }
    val characterCardInfo = remember { mutableStateMapOf<Int, BangumiCharacterCardInfo>() }
    val actorChineseNames = remember { mutableStateMapOf<Int, String>() }
    var myCollection by remember { mutableStateOf<BangumiMyCollection?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isCollectionLoaded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var draftType by remember { mutableIntStateOf(0) }
    var draftRate by remember { mutableIntStateOf(0) }
    var draftTags by remember { mutableStateOf(TextFieldValue()) }
    var isTagInputFocused by remember { mutableStateOf(false) }
    var tagInputRect by remember { mutableStateOf(Rect.Zero) }
    var myTagSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isMyTagsLoading by remember { mutableStateOf(false) }
    var hasRequestedMyTags by remember { mutableStateOf(false) }
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
    var editingWatchRecord by remember { mutableStateOf<ItemActivityRecord?>(null) }
    var editingWatchAmount by remember { mutableStateOf("") }

    LaunchedEffect(subjectId) {
        detailViewModel.load(subjectId)
    }

    LaunchedEffect(detailUiState) {
        isLoading = detailUiState.isLoading
        if (detailUiState.isLoading) return@LaunchedEffect
        val loadedDetail = detailUiState.detail
        val loadedPersons = detailUiState.persons
        val loadedCharacters = detailUiState.characters
        val loadedEpisodePage = detailUiState.episodes
        val loadedLegacySubject = detailUiState.legacySubject
        val loadedSubjectRelations = detailUiState.relatedSubjects
        detail = loadedDetail
        subjectPersons = loadedPersons
        subjectCharacters = loadedCharacters
        sequelSubjects = loadedSubjectRelations.filter { relatedSubject ->
            relatedSubject.type == 2 &&
                relatedSubject.relation?.trim() in setOf("续集", "續集")
        }
        characterChineseNames.clear()
        characterChineseNames.putAll(loadedLegacySubject?.crt.orEmpty()
            .mapNotNull { character ->
                val id = character.id ?: return@mapNotNull null
                val name = character.name_cn?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                id to name
            }
            .toMap())
        characterCardInfo.clear()
        actorChineseNames.clear()
        mainEpisodeCount = loadedEpisodePage?.total?.takeIf { it > 0 }
        mainEpisodes = loadedEpisodePage?.data.orEmpty()
            .sortedWith(compareBy(
                { it.ep ?: Double.MAX_VALUE },
                { it.sort ?: Double.MAX_VALUE }
            ))
            .map { BangumiUserEpisodeCollection(episode = it, type = 0) }
        isLoading = false
        if (loadedDetail == null) {
            Toast.makeText(context, detailLoadFailedText, Toast.LENGTH_SHORT).show()
        }
        val token = UserPrefs.getBangumiAccessToken(context)
        if (token.isNotEmpty()) {
            val collectionResult = runCatchingCancellable {
                BangumiAuthSession.execute(context) { service ->
                    var username = UserPrefs.getBangumiUsername(context)
                    if (username.isBlank()) {
                        username = service.getCurrentUser().username
                        UserPrefs.setBangumiUsername(context, username)
                    }
                    val collection = service.getMyCollection(username, subjectId)
                    val episodes = try {
                        service.getEpisodeCollections(subjectId)
                    } catch (error: HttpException) {
                        if (error.code() == 401) throw error
                        null
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                    Triple(username, collection, episodes)
                }
            }
            val loadedCollection = collectionResult.getOrNull()
            if (loadedCollection != null) {
                val (username, collection, loadedEpisodes) = loadedCollection
                val preferredTagOrder = withContext(Dispatchers.IO) {
                    BangumiTagOrder.get(context, username, subjectId)
                }
                val orderedCollection = collection.copy(
                    tags = BangumiTagOrder.restore(collection.tags, preferredTagOrder)
                )
                myCollection = orderedCollection
                draftType = orderedCollection.type ?: 1
                draftRate = orderedCollection.rate ?: 0
                val orderedTagText = orderedCollection.tags.orEmpty().joinToString(", ")
                draftTags = TextFieldValue(
                    text = orderedTagText,
                    selection = TextRange(orderedTagText.length)
                )
                draftComment = orderedCollection.comment.orEmpty()
                savedEpisodeProgress = orderedCollection.ep_status ?: 0
                savedRegularEpisodeProgress = savedEpisodeProgress
                draftEpisodeProgress = savedEpisodeProgress
                if (loadedEpisodes != null) {
                    mainEpisodes = loadedEpisodes.data.orEmpty()
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
                    val message = if ((error as? HttpException)?.code() == 401) {
                        authorizationExpiredText
                    } else {
                        collectionLoadFailedText
                    }
                    Toast.makeText(
                        context,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        isCollectionLoaded = true
    }

    LaunchedEffect(draftType, mainEpisodes.size, isCollectionLoaded) {
        if (isCollectionLoaded && draftType == 2 && mainEpisodes.isNotEmpty()) {
            draftEpisodeProgress = mainEpisodes.size
        }
    }

    val coroutineScope = rememberCoroutineScope()

    fun loadMyTagSuggestions() {
        if (hasRequestedMyTags || isMyTagsLoading) return
        val token = UserPrefs.getBangumiAccessToken(context)
        if (token.isEmpty()) return
        hasRequestedMyTags = true
        isMyTagsLoading = true
        coroutineScope.launch {
            var username = UserPrefs.getBangumiUsername(context)
            val cachedTagGroups = withContext(Dispatchers.IO) {
                val collectionTags = if (username.isBlank()) {
                    emptyList()
                } else {
                    BangumiPageCache.load(context, username)
                        ?.collections
                        .orEmpty()
                        .values
                        .flatten()
                        .map { it.tags.orEmpty() }
                }
                val locallyOrderedTags = if (username.isBlank()) {
                    emptyList()
                } else {
                    BangumiTagOrder.snapshot(context, username).values.toList()
                }
                collectionTags + locallyOrderedTags
            }
            myTagSuggestions = rankBangumiTags(cachedTagGroups)

            val remoteTagGroups = runCatchingCancellable {
                BangumiAuthSession.execute(context) { service ->
                    if (username.isBlank()) {
                        username = service.getCurrentUser().username
                        UserPrefs.setBangumiUsername(context, username)
                    }
                    val result = mutableListOf<List<String>>()
                    var offset = 0
                    while (true) {
                        val page = service.getUserCollections(
                            username = username,
                            subjectType = 2,
                            collectionType = null,
                            limit = 50,
                            offset = offset
                        )
                        val collections = page.data.orEmpty()
                        result += collections.map { it.tags.orEmpty() }
                        if (collections.isEmpty() || offset + collections.size >= page.total) {
                            break
                        }
                        offset += collections.size
                    }
                    result
                }
            }.getOrDefault(emptyList())
            myTagSuggestions = rankBangumiTags(remoteTagGroups + cachedTagGroups)
            isMyTagsLoading = false
        }
    }

    fun saveCollection() {
        val token = UserPrefs.getBangumiAccessToken(context)
        if (token.isEmpty() || isSaving || draftType == 0) return
        val tags = draftTags.text
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
                BangumiAuthSession.execute(context) { service ->
                    var username = UserPrefs.getBangumiUsername(context)
                    if (username.isBlank()) {
                        username = service.getCurrentUser().username
                        UserPrefs.setBangumiUsername(context, username)
                    }
                    val trimmedComment = draftComment.trim()
                    val isPrivate = myCollection?.private ?: false
                    val legacyStatus = legacyBangumiCollectionStatus(targetApiType)
                    val legacySaved = legacyStatus != null && runCatchingCancellable {
                        val legacyResponse = service.updateCollectionLegacy(
                            subjectId = subjectId,
                            appId = AppConfig.BANGUMI_CLIENT_ID,
                            status = legacyStatus,
                            tags = tags.joinToString(" "),
                            comment = trimmedComment,
                            rating = draftRate,
                            privacy = if (isPrivate) 1 else 0
                        )
                        if (
                            !legacyResponse.isSuccessful ||
                            legacyResponse.body()?.code != null ||
                            legacyResponse.body()?.error != null
                        ) {
                            return@runCatchingCancellable false
                        }
                        val verified = service.getMyCollection(username, subjectId)
                        verified.type == targetApiType &&
                            verified.rate == draftRate &&
                            verified.comment.orEmpty() == trimmedComment &&
                            verified.private == isPrivate &&
                            BangumiTagOrder.hasSameOrder(verified.tags, tags)
                    }.getOrDefault(false)

                    if (!legacySaved) {
                        val response = service.updateCollection(
                            subjectId,
                            BangumiCollectionUpdate(
                                type = targetApiType,
                                rate = draftRate,
                                comment = trimmedComment,
                                tags = tags,
                                `private` = isPrivate
                            )
                        )
                        if (!response.isSuccessful) throw HttpException(response)
                    }
                    withContext(Dispatchers.IO) {
                        BangumiTagOrder.save(context, username, subjectId, tags)
                    }

                    val episodesForSave = if (targetApiType == 2 && mainEpisodes.isEmpty()) {
                        runCatchingCancellable {
                            service.getEpisodeCollections(subjectId).data.orEmpty()
                                .sortedWith(compareBy(
                                    { it.episode.ep ?: Double.MAX_VALUE },
                                    { it.episode.sort ?: Double.MAX_VALUE }
                                ))
                        }.getOrDefault(emptyList())
                    } else {
                        mainEpisodes
                    }
                    val targetEpisodeProgress = if (targetApiType == 2) {
                        episodesForSave.size
                    } else {
                        draftEpisodeProgress
                    }
                    val boundedEpisodeProgress = targetEpisodeProgress.coerceIn(
                        0,
                        episodesForSave.size
                    )
                    val boundedSavedProgress = savedEpisodeProgress.coerceIn(
                        0,
                        episodesForSave.size
                    )
                    val changedEpisodes = when {
                        targetApiType == 2 ->
                            episodesForSave.filter { it.type != 2 } to 2
                        boundedEpisodeProgress > boundedSavedProgress ->
                            episodesForSave.subList(
                                boundedSavedProgress,
                                boundedEpisodeProgress
                            ) to 2
                        boundedEpisodeProgress < boundedSavedProgress ->
                            episodesForSave.subList(
                                boundedEpisodeProgress,
                                boundedSavedProgress
                            ) to 0
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
                    val updatedEpisodes = episodesForSave.map { episode ->
                        val type = if (episode.episode.id in changedEpisodeIds) {
                            changedEpisodes.second
                        } else {
                            episode.type
                        }
                        episode.copy(type = type)
                    }
                    val currentWatchedEpisodeIds = updatedEpisodes.mapNotNull { episode ->
                        episode.episode.id.takeIf { episode.type == 2 }
                    }

                    val refreshedCollection = service.getMyCollection(username, subjectId)
                    val recordedEpisodeProgress = if (targetApiType == 2) {
                        resolvedCompletedEpisodeCount(
                            loadedEpisodeCount = episodesForSave.size,
                            episodePageTotal = mainEpisodeCount,
                            subjectEpisodeCount = detail?.eps,
                            collectionEpisodeCount = refreshedCollection.ep_status
                        )
                    } else {
                        boundedEpisodeProgress
                    }
                    val orderedTags = BangumiTagOrder.restore(refreshedCollection.tags, tags)
                    myCollection = refreshedCollection.copy(tags = orderedTags)
                    val orderedTagText = orderedTags.orEmpty().joinToString(", ")
                    draftTags = TextFieldValue(
                        text = orderedTagText,
                        selection = TextRange(orderedTagText.length)
                    )
                    val knownSuggestionKeys = myTagSuggestions
                        .map { it.lowercase(Locale.ROOT) }
                        .toSet()
                    myTagSuggestions = myTagSuggestions + orderedTags.orEmpty().filter {
                        it.lowercase(Locale.ROOT) !in knownSuggestionKeys
                    }
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
                            currentEpisodes = recordedEpisodeProgress,
                            currentEpisodeIds = currentWatchedEpisodeIds
                        )
                    }
                    activityHistoryRevision++
                    mainEpisodes = updatedEpisodes
                    savedRegularEpisodeProgress = recordedEpisodeProgress
                    savedEpisodeProgress = recordedEpisodeProgress
                    draftEpisodeProgress = savedEpisodeProgress
                    val refreshedSubject = refreshedCollection.subject ?: detail?.let {
                        BangumiSubject(
                            id = subjectId,
                            name = it.name ?: subjectName,
                            name_cn = it.name_cn ?: subjectNameCn,
                            type = it.type ?: 2,
                            summary = it.summary,
                            eps = it.eps,
                            total_episodes = it.total_episodes,
                            rating = it.rating,
                            images = it.images,
                            date = it.date,
                            tags = it.tags
                        )
                    }
                    val cachedCollection = BangumiCollection(
                        subject_id = refreshedCollection.subject_id ?: subjectId,
                        subject_type = refreshedCollection.subject_type ?: 2,
                        rate = refreshedCollection.rate ?: draftRate,
                        type = bangumiCollectionTypeToUi(
                            refreshedCollection.type ?: targetApiType
                        ),
                        comment = refreshedCollection.comment,
                        tags = orderedTags,
                        ep_status = recordedEpisodeProgress,
                        vol_status = refreshedCollection.vol_status ?: 0,
                        updated_at = refreshedCollection.updated_at,
                        `private` = refreshedCollection.private ?: false,
                        subject = refreshedSubject
                    )
                    withContext(Dispatchers.IO) {
                        BangumiPageCache.updateCollection(
                            context = context,
                            username = username,
                            collection = cachedCollection,
                            rating = normalizeBangumiScore(refreshedSubject?.rating),
                            episodeTotal = mainEpisodeCount
                                ?: episodesForSave.size.takeIf { it > 0 }
                                ?: detail?.eps,
                            watchedEpisodeCount = recordedEpisodeProgress
                        )
                    }
                    BangumiViewModel.collectionChanged = true
                    Toast.makeText(
                        context,
                        collectionSavedText,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                val message = if ((e as? HttpException)?.code() == 401) {
                    authorizationExpiredText
                } else {
                    collectionSaveFailedText
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            isSaving = false
        }
    }

    val displayName = subjectNameCn.ifEmpty { subjectName }
    val score = normalizeBangumiScore(detail?.rating)
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
        1 to stringResource(R.string.bangumi_wish),
        3 to stringResource(R.string.bangumi_doing),
        2 to stringResource(R.string.bangumi_done),
        4 to stringResource(R.string.bangumi_on_hold),
        5 to stringResource(R.string.bangumi_dropped)
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

                Crossfade(
                    targetState = isLoading,
                    modifier = Modifier.weight(1f),
                    animationSpec = tween(DesignTokens.AnimDuration),
                    label = "anime_detail_loading"
                ) { loading ->
                if (loading) {
                    AnimeDetailLoadingSkeleton()
                } else if (detail != null) {
                    val d = detail ?: return@Crossfade
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
                            Column(modifier = Modifier.weight(1f).heightIn(min = 168.dp)) {
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
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.bangumi_my_rating),
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
                                                val ratingDescription = stringResource(
                                                    R.string.bangumi_rating_value,
                                                    i
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .semantics {
                                                            contentDescription = ratingDescription
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
                                                if (it.isFocused) loadMyTagSuggestions()
                                            },
                                        label = stringResource(R.string.bangumi_tags_hint),
                                        useLabelAsPlaceholder = true,
                                        singleLine = true
                                    )
                                    Spacer(Modifier.height(DesignTokens.SpaceLg))

                                    TextField(
                                        value = draftComment,
                                        onValueChange = { draftComment = it },
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
                                                onClick = { saveCollection() }
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.general_load_failed), color = dim)
                    }
                }
                }
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
                                                draftType = apiType
                                                if (apiType == 2 && mainEpisodes.isNotEmpty()) {
                                                    draftEpisodeProgress = mainEpisodes.size
                                                }
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

private fun rankBangumiTags(tagGroups: Iterable<List<String>>): List<String> {
    val labels = linkedMapOf<String, String>()
    val counts = mutableMapOf<String, Int>()
    val firstSeen = mutableMapOf<String, Int>()
    var nextOrder = 0
    tagGroups.forEach { tags ->
        tags.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .forEach { tag ->
                val key = tag.lowercase(Locale.ROOT)
                labels.putIfAbsent(key, tag)
                counts[key] = counts.getOrDefault(key, 0) + 1
                firstSeen.putIfAbsent(key, nextOrder++)
            }
    }
    return labels.keys
        .sortedWith(
            compareByDescending<String> { counts.getOrDefault(it, 0) }
                .thenBy { firstSeen.getOrDefault(it, Int.MAX_VALUE) }
        )
        .mapNotNull(labels::get)
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

internal fun resolvedCompletedEpisodeCount(
    loadedEpisodeCount: Int,
    episodePageTotal: Int?,
    subjectEpisodeCount: Int?,
    collectionEpisodeCount: Int?
): Int = sequenceOf(
    loadedEpisodeCount,
    episodePageTotal,
    subjectEpisodeCount,
    collectionEpisodeCount
).filterNotNull().firstOrNull { it > 0 } ?: 0

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
            bottom = DesignTokens.SpaceXl
        ),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.SpaceLg)
    ) {
        Spacer(Modifier.height(DesignTokens.SpaceXs))
        LoadingSkeletonBlock(Modifier.fillMaxWidth().height(DesignTokens.ButtonHeight))
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

private fun legacyBangumiCollectionStatus(apiType: Int): String? = when (apiType) {
    1 -> "wish"
    2 -> "collect"
    3 -> "do"
    4 -> "on_hold"
    5 -> "dropped"
    else -> null
}
