package com.example.gamearchive

import androidx.compose.ui.res.stringResource
import android.content.Context
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SteamAchievementItem(
    val apiName: String,
    val displayName: String,
    val description: String?,
    val iconUrl: String?,
    val unlocked: Boolean,
    val unlockTime: Long,
    val hidden: Boolean,
    val globalUnlockPercent: Float?
)

private const val RARE_ACHIEVEMENT_PERCENT = 10f

@Composable
fun SteamAchievementsSection(
    appId: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dim = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
    var expanded by remember(appId) { mutableStateOf(false) }
    var loading by remember(appId) { mutableStateOf(false) }
    var loaded by remember(appId) { mutableStateOf(false) }
    var achievements by remember(appId) { mutableStateOf<List<SteamAchievementItem>>(emptyList()) }
    var unlockedExpanded by remember(appId) { mutableStateOf(true) }
    var lockedExpanded by remember(appId) { mutableStateOf(true) }

    LaunchedEffect(expanded, appId) {
        if (!expanded || loading || loaded || appId == 0) return@LaunchedEffect
        loading = true
        achievements = withContext(Dispatchers.IO) {
            runCatchingCancellable {
                loadSteamAchievements(context.applicationContext, appId)
            }
                .onFailure {
                    Log.w("SteamAchievements", "Failed to load achievements for appId=$appId", it)
                }
                .getOrDefault(emptyList())
        }
        loading = false
        loaded = true
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ExpandableSectionTrigger(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            contentPadding = PaddingValues(vertical = DesignTokens.SpaceLg),
            arrowColor = MiuixTheme.colorScheme.onSurface.copy(
                alpha = DesignTokens.OpacityHint
            )
        ) {
            Text(
                text = stringResource(R.string.achievement_title),
                fontSize = DesignTokens.TextBody1.sp,
                fontWeight = FontWeight.Bold,
                color = dim,
                modifier = Modifier.weight(1f)
            )
        }

        ExpandableSectionContent(expanded = expanded) {
            val rareGlowTransition = rememberInfiniteTransition(
                label = "rare_achievement_glow"
            )
            val rareGlowRotation = rareGlowTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2_400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rare_achievement_rotation"
            )
            val rareGlowPulse = rareGlowTransition.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rare_achievement_pulse"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = DesignTokens.SpaceXl,
                        end = DesignTokens.SpaceXl,
                        bottom = DesignTokens.SpaceLg
                    )
            ) {
                when {
                    loading -> SteamAchievementsLoadingSkeleton()
                    achievements.isEmpty() -> Text(
                        text = stringResource(R.string.achievement_unavailable),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = dim
                    )
                    else -> {
                        val unlockedAchievements = achievements.filter { it.unlocked }
                        val lockedAchievements = achievements.filterNot { it.unlocked }
                        AchievementGroup(
                            title = stringResource(
                                R.string.achievement_progress,
                                unlockedAchievements.size,
                                achievements.size
                            ),
                            achievements = unlockedAchievements,
                            expanded = unlockedExpanded,
                            onExpandedChange = { unlockedExpanded = it },
                            rareGlowRotation = rareGlowRotation,
                            rareGlowPulse = rareGlowPulse
                        )
                        Spacer(Modifier.height(DesignTokens.SpaceLg))
                        AchievementGroup(
                            title = stringResource(
                                R.string.achievement_locked_progress,
                                lockedAchievements.size,
                                achievements.size
                            ),
                            achievements = lockedAchievements,
                            expanded = lockedExpanded,
                            onExpandedChange = { lockedExpanded = it },
                            rareGlowRotation = rareGlowRotation,
                            rareGlowPulse = rareGlowPulse
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementGroup(
    title: String,
    achievements: List<SteamAchievementItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    rareGlowRotation: State<Float>,
    rareGlowPulse: State<Float>
) {
    ExpandableSectionTrigger(
        expanded = expanded,
        onToggle = { onExpandedChange(!expanded) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
            .background(DesignTokens.AccentBlue.copy(alpha = 0.11f)),
        contentPadding = PaddingValues(
            horizontal = DesignTokens.SpaceLg,
            vertical = DesignTokens.SpaceMd
        ),
        arrowColor = DesignTokens.AccentBlue.copy(
            alpha = DesignTokens.OpacityEmphasis
        ),
        expandedArrowColor = DesignTokens.AccentBlue
    ) {
        Text(
            text = title,
            fontSize = DesignTokens.TextBody2.sp,
            fontWeight = FontWeight.Bold,
            color = DesignTokens.AccentBlue,
            modifier = Modifier.weight(1f)
        )
    }
    ExpandableSectionContent(expanded = expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DesignTokens.SpaceLg)
        ) {
            achievements.forEachIndexed { index, achievement ->
                if (index > 0) Spacer(Modifier.height(DesignTokens.SpaceLg))
                SteamAchievementRow(
                    achievement = achievement,
                    rareGlowRotation = rareGlowRotation,
                    rareGlowPulse = rareGlowPulse
                )
            }
        }
    }
}

@Composable
private fun SteamAchievementRow(
    achievement: SteamAchievementItem,
    rareGlowRotation: State<Float>,
    rareGlowPulse: State<Float>
) {
    val context = LocalContext.current
    val dim = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
    val isRare = achievement.unlocked &&
        achievement.globalUnlockPercent?.let { it < RARE_ACHIEVEMENT_PERCENT } == true
    val title = if (achievement.hidden && !achievement.unlocked) {
        stringResource(R.string.achievement_hidden)
    } else {
        achievement.displayName
    }
    val iconModel = remember(achievement.iconUrl) {
        achievement.iconUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .build()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SteamAchievementIcon(
            iconModel = iconModel,
            contentDescription = title,
            rare = isRare,
            glowRotation = rareGlowRotation,
            glowPulse = rareGlowPulse
        )
        Spacer(Modifier.width(DesignTokens.SpaceLg))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = DesignTokens.TextBody1.sp,
                fontWeight = FontWeight.Bold,
                color = if (achievement.unlocked) MiuixTheme.colorScheme.onSurface else dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!achievement.hidden || achievement.unlocked) {
                achievement.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(Modifier.height(DesignTokens.SpaceXxs))
                    Text(
                        text = description,
                        fontSize = DesignTokens.TextBody2.sp,
                        color = dim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(DesignTokens.SpaceXxs))
            Text(
                text = if (achievement.unlocked && achievement.unlockTime > 0L) {
                    stringResource(
                        R.string.achievement_unlocked_date,
                        formatAchievementDate(achievement.unlockTime)
                    )
                } else if (achievement.unlocked) {
                    stringResource(R.string.achievement_unlocked)
                } else {
                    stringResource(R.string.achievement_locked)
                },
                fontSize = DesignTokens.TextCaption.sp,
                color = dim
            )
        }
    }
}

@Composable
private fun SteamAchievementIcon(
    iconModel: ImageRequest?,
    contentDescription: String,
    rare: Boolean,
    glowRotation: State<Float>,
    glowPulse: State<Float>
) {
    val gold = Color(0xFFFFC84A)
    val paleGold = Color(0xFFFFE9A3)

    Box(modifier = Modifier.size(52.dp)) {
        if (rare) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cornerRadius = CornerRadius(DesignTokens.CornerMedium.toPx())
                drawRoundRect(
                    color = gold.copy(alpha = 0.16f + 0.12f * glowPulse.value),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = 5.dp.toPx())
                )
                rotate(degrees = glowRotation.value) {
                    drawRoundRect(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                gold.copy(alpha = 0.25f),
                                paleGold,
                                Color.White,
                                gold,
                                gold.copy(alpha = 0.25f)
                            ),
                            center = center
                        ),
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (rare) 2.dp else 0.dp)
                .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            if (iconModel != null) {
                AsyncImage(
                    model = iconModel,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun SteamAchievementsLoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.SpaceLg)
    ) {
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LoadingSkeletonBlock(
                    modifier = Modifier.size(52.dp),
                    cornerRadius = DesignTokens.CornerMedium
                )
                Spacer(Modifier.width(DesignTokens.SpaceLg))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.SpaceSm)
                ) {
                    LoadingSkeletonBlock(Modifier.fillMaxWidth(0.55f).height(14.dp))
                    LoadingSkeletonBlock(Modifier.fillMaxWidth(0.86f).height(11.dp))
                    LoadingSkeletonBlock(Modifier.fillMaxWidth(0.32f).height(10.dp))
                }
            }
        }
    }
}

private suspend fun loadSteamAchievements(
    context: Context,
    appId: Int
): List<SteamAchievementItem> = coroutineScope {
    val accounts = UserPrefs.getAllAccounts(context)
    if (accounts.isEmpty()) return@coroutineScope emptyList()

    val language = LocaleHelper.getApiLanguage(context)
    val api = GameArchiveApp.apiService
    val schemaRequest = async {
        runCatchingCancellable {
            api.getAchievementSchema(
                key = accounts.first().second,
                appId = appId,
                language = language
            )
        }.getOrNull()
    }
    val globalPercentagesRequest = async {
        runCatchingCancellable {
            api.getGlobalAchievementPercentages(appId)
        }.getOrNull()
    }
    val playerRequests = accounts.map { (steamId, apiKey) ->
        async {
            runCatchingCancellable {
                api.getPlayerAchievements(
                    key = apiKey,
                    steamId = steamId,
                    appId = appId,
                    language = language
                ).playerstats
            }.getOrNull()
        }
    }

    val schema = schemaRequest.await()
    val globalPercentByName = globalPercentagesRequest.await()
        ?.achievementpercentages
        ?.achievements
        .orEmpty()
        .associate { it.name to it.percent }
    val playerStats = playerRequests.awaitAll()
        .filterNotNull()
        .filter { it.success != false }
    if (playerStats.isEmpty()) return@coroutineScope emptyList()

    val progressByName = playerStats
        .flatMap { it.achievements.orEmpty() }
        .groupBy { it.apiname }
        .mapValues { (_, values) ->
            PlayerAchievement(
                apiname = values.first().apiname,
                achieved = if (values.any { it.achieved > 0 }) 1 else 0,
                unlocktime = values.maxOfOrNull { it.unlocktime } ?: 0L
            )
        }

    val definitions = schema?.game?.availableGameStats?.achievements.orEmpty()
    val merged = if (definitions.isNotEmpty()) {
        definitions.map { definition ->
            val progress = progressByName[definition.name]
            val unlocked = progress?.achieved == 1
            SteamAchievementItem(
                apiName = definition.name,
                displayName = definition.displayName?.takeIf { it.isNotBlank() }
                    ?: definition.name,
                description = definition.description,
                iconUrl = normalizeSteamImageUrl(
                    if (unlocked) definition.icon else definition.icongray
                ),
                unlocked = unlocked,
                unlockTime = progress?.unlocktime ?: 0L,
                hidden = definition.hidden == 1,
                globalUnlockPercent = globalPercentByName[definition.name]
            )
        }
    } else {
        progressByName.values.map { progress ->
            SteamAchievementItem(
                apiName = progress.apiname,
                displayName = progress.apiname,
                description = null,
                iconUrl = null,
                unlocked = progress.achieved == 1,
                unlockTime = progress.unlocktime,
                hidden = false,
                globalUnlockPercent = globalPercentByName[progress.apiname]
            )
        }
    }

    merged.sortedWith(
        compareByDescending<SteamAchievementItem> { it.unlocked }
            .thenByDescending { if (it.unlocked) it.unlockTime else 0L }
            .thenBy { it.displayName.lowercase(Locale.ROOT) }
    )
}

private fun normalizeSteamImageUrl(url: String?): String? {
    return url?.takeIf { it.isNotBlank() }?.replace("http://", "https://")
}

private fun formatAchievementDate(unlockTime: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return formatter.format(Date(unlockTime * 1_000L))
}
