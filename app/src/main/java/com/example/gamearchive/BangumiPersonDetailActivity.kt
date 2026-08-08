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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class BangumiPersonWork(
    val subject: BangumiPersonSubject,
    val roles: List<String>
)

@Suppress("DEPRECATION")
class BangumiPersonDetailActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeUtils.applyTheme(this)

        val personId = intent.getIntExtra(EXTRA_PERSON_ID, 0)
        val personName = intent.getStringExtra(EXTRA_PERSON_NAME).orEmpty()
        val characterId = intent.getIntExtra(EXTRA_CHARACTER_ID, 0)
        val characterName = intent.getStringExtra(EXTRA_CHARACTER_NAME).orEmpty()
        setContent {
            MiuixThemeForApp {
                if (characterId > 0) {
                    BangumiCharacterDetailScreen(
                        characterId = characterId,
                        initialName = characterName,
                        onBack = { finish() }
                    )
                } else {
                    BangumiPersonDetailScreen(
                        personId = personId,
                        initialName = personName,
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_PERSON_ID = "PERSON_ID"
        private const val EXTRA_PERSON_NAME = "PERSON_NAME"
        private const val EXTRA_CHARACTER_ID = "CHARACTER_ID"
        private const val EXTRA_CHARACTER_NAME = "CHARACTER_NAME"

        fun createIntent(context: Context, personId: Int, personName: String): Intent =
            Intent(context, BangumiPersonDetailActivity::class.java).apply {
                putExtra(EXTRA_PERSON_ID, personId)
                putExtra(EXTRA_PERSON_NAME, personName)
            }

        fun createCharacterIntent(
            context: Context,
            characterId: Int,
            characterName: String
        ): Intent = Intent(context, BangumiPersonDetailActivity::class.java).apply {
            putExtra(EXTRA_CHARACTER_ID, characterId)
            putExtra(EXTRA_CHARACTER_NAME, characterName)
        }
    }
}

@Composable
private fun BangumiPersonDetailScreen(
    personId: Int,
    initialName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var detail by remember(personId) { mutableStateOf<BangumiPersonDetail?>(null) }
    var works by remember(personId) { mutableStateOf<List<BangumiPersonWork>>(emptyList()) }
    var isLoading by remember(personId) { mutableStateOf(true) }
    var detailFailed by remember(personId) { mutableStateOf(false) }
    var worksFailed by remember(personId) { mutableStateOf(false) }
    var loadRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(personId, loadRevision) {
        isLoading = true
        detailFailed = false
        worksFailed = false
        val (detailResult, worksResult) = coroutineScope {
            val loadedDetail = async { runCatching { GameArchiveApp.bgmService.getPerson(personId) } }
            val loadedWorks = async {
                runCatching { GameArchiveApp.bgmService.getPersonSubjects(personId) }
            }
            loadedDetail.await() to loadedWorks.await()
        }
        detail = detailResult.getOrNull()
        detailFailed = detailResult.isFailure
        works = worksResult.getOrNull()?.let(::mergePersonWorks).orEmpty()
        worksFailed = worksResult.isFailure
        isLoading = false
    }

    val pageTitle = personChineseName(detail).ifBlank {
        initialName.ifBlank { detail?.name.orEmpty() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarHeightDp() + 4.dp, end = 12.dp, bottom = 4.dp),
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
                    text = pageTitle,
                    fontSize = DesignTokens.TextHeadline.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = DesignTokens.SpaceXs)
                )
            }

            Crossfade(
                targetState = isLoading,
                animationSpec = tween(DesignTokens.AnimDuration),
                label = "bangumi_person_loading",
                modifier = Modifier.weight(1f)
            ) { loading ->
                if (loading) {
                    AnimeDetailLoadingSkeleton()
                } else if (detailFailed || detail == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = context.getString(R.string.general_load_failed),
                                color = MiuixTheme.colorScheme.onSurface.copy(
                                    alpha = DesignTokens.OpacityBody
                                )
                            )
                            Spacer(Modifier.height(DesignTokens.SpaceMd))
                            TextButton(
                                text = context.getString(R.string.general_retry),
                                onClick = { loadRevision++ }
                            )
                        }
                    }
                } else {
                    BangumiPersonContent(
                        detail = detail!!,
                        initialName = initialName,
                        works = works,
                        worksFailed = worksFailed
                    )
                }
            }
        }
    }
}

@Composable
private fun BangumiPersonContent(
    detail: BangumiPersonDetail,
    initialName: String,
    works: List<BangumiPersonWork>,
    worksFailed: Boolean
) {
    val context = LocalContext.current
    val dim = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
    val chineseName = personChineseName(detail).ifBlank { initialName }
    val originalName = detail.name.orEmpty()
    val imageUrl = detail.images?.large
        ?: detail.images?.common
        ?: detail.images?.medium
        ?: detail.images?.small
        ?: ""
    val infoboxRows = remember(detail.infobox) {
        detail.infobox.orEmpty().mapNotNull { item ->
            val values = flattenBangumiInfoboxValue(item.value)
            if (values.isEmpty()) null else item.key.trim() to values
        }
    }
    val ratingMode = UserPrefs.getBangumiRatingMode(context)
    var myRatings by remember(detail.id) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    LaunchedEffect(detail.id) {
        val username = UserPrefs.getBangumiUsername(context)
        if (username.isNotBlank()) {
            myRatings = withContext(Dispatchers.IO) {
                BangumiPageCache.load(context, username)
                    ?.collections
                    .orEmpty()
                    .values
                    .flatten()
                    .filter { it.rate > 0 }
                    .associate { it.subject_id to it.rate }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            bottom = DesignTokens.SpaceMassive
        )
    ) {
        item("person_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = DesignTokens.SpaceXl,
                        vertical = DesignTokens.SpaceLg
                    ),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpaceLg)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(imageUrl).crossfade(true).build(),
                    contentDescription = originalName,
                    modifier = Modifier
                        .width(120.dp)
                        .height(168.dp)
                        .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                        .background(MiuixTheme.colorScheme.secondaryContainer),
                    contentScale = if (detail.type == 2) ContentScale.Fit else ContentScale.Crop
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chineseName.ifBlank { originalName },
                        fontSize = DesignTokens.TextHeadline.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    if (chineseName.isNotBlank() && originalName.isNotBlank() &&
                        !chineseName.equals(originalName, ignoreCase = true)
                    ) {
                        Spacer(Modifier.height(DesignTokens.SpaceXs))
                        Text(
                            text = originalName,
                            fontSize = DesignTokens.TextBody1.sp,
                            color = dim
                        )
                    }
                    Spacer(Modifier.height(DesignTokens.SpaceMd))
                    Text(
                        text = context.getString(
                            if (detail.type == 2) R.string.bangumi_company
                            else R.string.bangumi_person
                        ),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = dim
                    )
                    val careers = detail.career.orEmpty().map { bangumiCareerLabel(context, it) }
                    if (careers.isNotEmpty()) {
                        Spacer(Modifier.height(DesignTokens.SpaceXs))
                        Text(
                            text = careers.joinToString(" · "),
                            fontSize = DesignTokens.TextBody1.sp,
                            color = dim
                        )
                    }
                }
            }
        }

        if (infoboxRows.isNotEmpty()) {
            item("person_details_title") {
                SectionTitle(
                    title = context.getString(R.string.bangumi_details_title),
                    modifier = Modifier.padding(horizontal = DesignTokens.SpaceXl)
                )
            }
            items(infoboxRows) { (key, values) ->
                BangumiPersonInfoboxRow(
                    key = key,
                    values = values,
                    modifier = Modifier.padding(horizontal = DesignTokens.SpaceXl)
                )
            }
        }

        if (!detail.summary.isNullOrBlank()) {
            item("person_summary") {
                Column(modifier = Modifier.padding(horizontal = DesignTokens.SpaceXl)) {
                    Spacer(Modifier.height(DesignTokens.SpaceXxl))
                    SectionTitle(context.getString(R.string.bangumi_summary_title))
                    Spacer(Modifier.height(DesignTokens.SpaceMd))
                    Text(
                        text = detail.summary,
                        fontSize = DesignTokens.TextBody1.sp,
                        color = dim,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        item("person_works_title") {
            Column(modifier = Modifier.padding(horizontal = DesignTokens.SpaceXl)) {
                Spacer(Modifier.height(DesignTokens.SpaceXxl))
                SectionTitle(context.getString(R.string.bangumi_person_works))
                Spacer(Modifier.height(DesignTokens.SpaceMd))
                if (worksFailed) {
                    Text(
                        text = context.getString(R.string.general_load_failed),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = dim
                    )
                } else if (works.isEmpty()) {
                    Text(
                        text = context.getString(R.string.general_no_data),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = dim
                    )
                }
            }
        }
        if (!worksFailed) {
            itemsIndexed(works, key = { _, work -> "work_${work.subject.id}" }) { index, work ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp))
                }
                BangumiPersonWorkItem(
                    work = work,
                    ratingMode = ratingMode,
                    myRate = myRatings[work.subject.id] ?: 0
                )
            }
        }
    }
}

@Composable
internal fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        fontSize = DesignTokens.TextSubtitle.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

@Composable
internal fun BangumiPersonInfoboxRow(
    key: String,
    values: List<BangumiInfoboxValue>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isWebsite = isWebsiteInfoboxKey(key)
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = DesignTokens.SpaceXs)
    ) {
        Text(
            text = key,
            fontSize = DesignTokens.TextBody2.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody),
            modifier = Modifier.width(100.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            values.forEach { value ->
                val text = value.label?.let { "$it: ${value.text}" } ?: value.text
                Text(
                    text = text,
                    fontSize = DesignTokens.TextBody2.sp,
                    color = if (isWebsite) DesignTokens.AccentBlue
                    else MiuixTheme.colorScheme.onSurface,
                    modifier = if (isWebsite) {
                        Modifier.motionClickable { openExternalWebLink(context, value.text) }
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

@Composable
private fun BangumiPersonWorkItem(
    work: BangumiPersonWork,
    ratingMode: Int,
    myRate: Int
) {
    val context = LocalContext.current
    val subject = work.subject
    val personEpisodeCount = subject.eps?.trim()?.toIntOrNull()?.takeIf { it > 0 }
    var subjectDetail by remember(subject.id) { mutableStateOf<BangumiSubjectDetail?>(null) }
    var episodeTotal by remember(subject.id) { mutableStateOf(personEpisodeCount) }
    LaunchedEffect(subject.id) {
        val loadedDetail = runCatching {
            GameArchiveApp.bgmService.getSubject(subject.id)
        }.getOrNull()
        subjectDetail = loadedDetail
        val knownTotal = loadedDetail?.eps?.takeIf { it > 0 } ?: personEpisodeCount
        episodeTotal = knownTotal ?: runCatching {
            GameArchiveApp.bgmService.getSubjectEpisodes(subject.id).total.takeIf { it > 0 }
        }.getOrNull()
    }
    val detail = subjectDetail
    val image = detail?.images?.common
        ?: detail?.images?.medium
        ?: subject.image
    val cardItem = remember(subject, detail, episodeTotal, myRate) {
        BangumiCollection(
            subject_id = subject.id,
            subject_type = 2,
            rate = myRate,
            type = 1,
            comment = null,
            tags = null,
            ep_status = 0,
            vol_status = 0,
            updated_at = null,
            `private` = false,
            subject = BangumiSubject(
                id = subject.id,
                name = detail?.name.orEmpty().ifBlank { subject.name.orEmpty() },
                name_cn = detail?.name_cn?.takeIf { it.isNotBlank() } ?: subject.name_cn,
                type = 2,
                summary = null,
                eps = episodeTotal,
                total_episodes = episodeTotal,
                rating = detail?.rating,
                images = BangumiImages(
                    large = image,
                    common = image,
                    medium = image,
                    small = image,
                    grid = image
                ),
                date = null,
                tags = null
            )
        )
    }
    BangumiItem(
        item = cardItem,
        type = 1,
        ratingMode = ratingMode,
        ratings = detail?.rating?.let { mapOf(subject.id to it) }.orEmpty(),
        episodeTotals = episodeTotal?.let { mapOf(subject.id to it) }.orEmpty(),
        watchedEpisodeCounts = emptyMap(),
        onClick = {
            context.startActivity(
                Intent(context, BangumiDetailActivity::class.java).apply {
                    putExtra("SUBJECT_ID", subject.id)
                    putExtra("SUBJECT_NAME", subject.name.orEmpty())
                    putExtra("SUBJECT_NAME_CN", subject.name_cn.orEmpty())
                    putExtra("SUBJECT_IMAGE", subject.image.orEmpty())
                }
            )
        }
    )
}

private fun personChineseName(detail: BangumiPersonDetail?): String {
    return bangumiChineseName(detail?.infobox)
}

private fun mergePersonWorks(subjects: List<BangumiPersonSubject>): List<BangumiPersonWork> {
    val merged = LinkedHashMap<Int, Pair<BangumiPersonSubject, LinkedHashSet<String>>>()
    subjects.asSequence().filter { it.type == 2 }.forEach { subject ->
        val entry = merged.getOrPut(subject.id) { subject to linkedSetOf() }
        subject.staff?.trim()?.takeIf { it.isNotEmpty() }?.let(entry.second::add)
    }
    return merged.values.map { (subject, roles) -> BangumiPersonWork(subject, roles.toList()) }
}

private fun bangumiCareerLabel(context: Context, career: String): String = when (career.lowercase()) {
    "producer" -> context.getString(R.string.bangumi_career_producer)
    "seiyu" -> context.getString(R.string.bangumi_career_seiyu)
    "artist" -> context.getString(R.string.bangumi_career_artist)
    "writer" -> context.getString(R.string.bangumi_career_writer)
    "illustrator" -> context.getString(R.string.bangumi_career_illustrator)
    "actor" -> context.getString(R.string.bangumi_career_actor)
    else -> career
}
