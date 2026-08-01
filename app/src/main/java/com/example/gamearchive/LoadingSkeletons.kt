package com.example.gamearchive

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun loadingSkeletonBaseColor(): Color = lerp(
    MiuixTheme.colorScheme.surface,
    MiuixTheme.colorScheme.surfaceVariant,
    0.58f
)

@Composable
internal fun rememberLoadingSkeletonBrush(
    baseColor: Color? = null,
    highlightColor: Color? = null
): Brush {
    val base = baseColor ?: loadingSkeletonBaseColor()
    val highlight = highlightColor
        ?: lerp(base, MiuixTheme.colorScheme.onSurface, 0.035f)
    val transition = rememberInfiniteTransition(label = "loading_skeleton")
    val offset by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loading_skeleton_offset"
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(offset - 240f, 0f),
        end = Offset(offset + 240f, 0f)
    )
}

@Composable
internal fun LoadingSkeletonBlock(
    modifier: Modifier,
    cornerRadius: Dp = 6.dp,
    baseColor: Color? = null,
    highlightColor: Color? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(rememberLoadingSkeletonBrush(baseColor, highlightColor))
    )
}

@Composable
internal fun GameDetailLoadingSkeleton(topInset: Dp) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset + 8.dp, bottom = 48.dp)
    ) {
        item("media") {
            LoadingSkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.SpaceLg)
                    .aspectRatio(16f / 9f),
                cornerRadius = DesignTokens.CornerMedium
            )
        }
        item("summary") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.SpaceXl),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpaceLg)
            ) {
                LoadingSkeletonBlock(Modifier.weight(1f).height(72.dp))
                LoadingSkeletonBlock(Modifier.weight(1f).height(72.dp))
            }
        }
        item("details") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.SpaceXl),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.SpaceLg)
            ) {
                LoadingSkeletonBlock(Modifier.fillMaxWidth(0.46f).height(20.dp))
                repeat(4) {
                    LoadingSkeletonBlock(Modifier.fillMaxWidth().height(14.dp))
                }
                LoadingSkeletonBlock(Modifier.fillMaxWidth(0.72f).height(14.dp))
                Spacer(Modifier.height(DesignTokens.SpaceXl))
                LoadingSkeletonBlock(Modifier.fillMaxWidth().height(150.dp))
            }
        }
    }
}

@Composable
internal fun AnimeDetailLoadingSkeleton() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = DesignTokens.SpaceXl,
            vertical = DesignTokens.SpaceLg
        ),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.SpaceLg)
    ) {
        item("header") {
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpaceLg)) {
                LoadingSkeletonBlock(
                    modifier = Modifier.width(120.dp).height(168.dp),
                    cornerRadius = DesignTokens.CornerMedium
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.SpaceMd)
                ) {
                    LoadingSkeletonBlock(Modifier.fillMaxWidth().height(18.dp))
                    LoadingSkeletonBlock(Modifier.fillMaxWidth(0.72f).height(14.dp))
                    LoadingSkeletonBlock(Modifier.fillMaxWidth(0.5f).height(14.dp))
                }
            }
        }
        item("progress") {
            LoadingSkeletonBlock(
                modifier = Modifier.fillMaxWidth().height(128.dp),
                cornerRadius = DesignTokens.CornerLarge
            )
        }
        items(5, key = { "anime_detail_line_$it" }) { index ->
            LoadingSkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(if (index == 4) 0.68f else 1f)
                    .height(14.dp)
            )
        }
    }
}

@Composable
internal fun BangumiSearchLoadingSkeleton() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = DesignTokens.SpaceXs,
            bottom = DesignTokens.SpaceMassive
        )
    ) {
        items(6, key = { "search_skeleton_$it" }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.SpaceXl, vertical = DesignTokens.SpaceMd),
                verticalAlignment = Alignment.Top
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
                    Spacer(Modifier.weight(1f))
                    LoadingSkeletonBlock(Modifier.fillMaxWidth(0.34f).height(12.dp))
                }
            }
        }
    }
}

@Composable
internal fun ActivityPageLoadingSkeleton(topInset: Dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset, start = DesignTokens.SpaceXl, end = DesignTokens.SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(DesignTokens.SpaceXl))
        LoadingSkeletonBlock(Modifier.width(128.dp).height(42.dp))
        Spacer(Modifier.height(DesignTokens.SpaceXl))
        LoadingSkeletonBlock(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f),
            cornerRadius = DesignTokens.CornerMedium
        )
        Spacer(Modifier.height(DesignTokens.SpaceXxl))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpaceMd)
        ) {
            repeat(4) {
                LoadingSkeletonBlock(
                    modifier = Modifier.weight(1f).aspectRatio(2f / 3f),
                    cornerRadius = DesignTokens.CornerMedium
                )
            }
        }
    }
}

@Composable
internal fun MediaLoadingSkeleton() {
    LoadingSkeletonBlock(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .aspectRatio(16f / 9f),
        cornerRadius = DesignTokens.CornerMedium,
        baseColor = Color.White.copy(alpha = 0.06f),
        highlightColor = Color.White.copy(alpha = 0.18f)
    )
}
