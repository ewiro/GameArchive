package com.example.gamearchive

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
internal fun Modifier.scrollLinkedTopBar(
    listState: LazyListState,
    height: Dp
): Modifier {
    val statusBarHidden by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    AutoHideStatusBar(statusBarHidden)
    val heightPx = with(LocalDensity.current) { height.toPx() }
    return graphicsLayer {
        val scrollOffsetPx = if (listState.firstVisibleItemIndex == 0) {
            listState.firstVisibleItemScrollOffset.toFloat()
        } else {
            heightPx
        }
        translationY = -scrollOffsetPx.coerceIn(0f, heightPx)
    }
}

@Composable
internal fun Modifier.scrollLinkedTopBar(
    scrollState: ScrollState,
    height: Dp
): Modifier {
    val statusBarHidden by remember(scrollState) {
        derivedStateOf { scrollState.value > 0 }
    }
    AutoHideStatusBar(statusBarHidden)
    val heightPx = with(LocalDensity.current) { height.toPx() }
    return graphicsLayer {
        translationY = -scrollState.value.toFloat().coerceIn(0f, heightPx)
    }
}

@Composable
private fun AutoHideStatusBar(hidden: Boolean) {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    val controller = remember(activity, view) {
        WindowCompat.getInsetsController(activity.window, view)
    }

    DisposableEffect(activity, controller) {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.statusBars())
            ThemeUtils.applyStatusBarAppearance(activity)
        }
    }
    LaunchedEffect(controller, hidden) {
        if (hidden) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}
