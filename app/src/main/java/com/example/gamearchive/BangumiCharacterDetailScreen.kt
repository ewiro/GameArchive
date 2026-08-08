package com.example.gamearchive

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BangumiCharacterDetailScreen(
    characterId: Int,
    initialName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var detail by remember(characterId) { mutableStateOf<BangumiCharacterDetail?>(null) }
    var isLoading by remember(characterId) { mutableStateOf(true) }
    var loadFailed by remember(characterId) { mutableStateOf(false) }
    var loadRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(characterId, loadRevision) {
        isLoading = true
        loadFailed = false
        val result = runCatching { GameArchiveApp.bgmService.getCharacter(characterId) }
        detail = result.getOrNull()
        loadFailed = result.isFailure
        isLoading = false
    }

    val pageTitle = characterChineseName(detail).ifBlank {
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
                label = "bangumi_character_loading",
                modifier = Modifier.weight(1f)
            ) { loading ->
                if (loading) {
                    AnimeDetailLoadingSkeleton()
                } else if (loadFailed || detail == null) {
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
                    BangumiCharacterContent(detail = detail!!, initialName = initialName)
                }
            }
        }
    }
}

@Composable
private fun BangumiCharacterContent(
    detail: BangumiCharacterDetail,
    initialName: String
) {
    val context = LocalContext.current
    val dim = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)
    val chineseName = characterChineseName(detail).ifBlank { initialName }
    val originalName = detail.name.orEmpty()
    val imageUrl = detail.images?.large
        ?: detail.images?.medium
        ?: detail.images?.small
        ?: ""
    val infoboxRows = remember(detail.infobox) {
        detail.infobox.orEmpty().mapNotNull { item ->
            val values = flattenBangumiInfoboxValue(item.value)
            if (values.isEmpty()) null else item.key.trim() to values
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = DesignTokens.SpaceMassive)
    ) {
        item("character_header") {
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
                    contentDescription = chineseName.ifBlank { originalName },
                    modifier = Modifier
                        .width(120.dp)
                        .height(168.dp)
                        .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                        .background(MiuixTheme.colorScheme.secondaryContainer),
                    contentScale = ContentScale.Fit
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
                        text = context.getString(R.string.bangumi_character),
                        fontSize = DesignTokens.TextBody1.sp,
                        color = dim
                    )
                }
            }
        }

        if (infoboxRows.isNotEmpty()) {
            item("character_details_title") {
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
            item("character_summary") {
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
    }
}

private fun characterChineseName(detail: BangumiCharacterDetail?): String {
    return detail?.name_cn?.takeIf { it.isNotBlank() }
        ?: bangumiChineseName(detail?.infobox)
}
