package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Suppress("DEPRECATION")
class WishlistActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeUtils.applyTheme(this)
        setContent {
            MiuixThemeForApp {
                WishlistScreen(
                    onBack = { finish() },
                    onOpenGame = { game ->
                        startActivity(Intent(this, DetailActivity::class.java).apply {
                            putExtra("APP_ID", game.id)
                            putExtra("APP_NAME", game.name)
                            putExtra("APP_PRICE", game.finalPriceStr)
                        })
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                )
            }
        }
    }
}

@Composable
private fun WishlistScreen(
    onBack: () -> Unit,
    onOpenGame: (MarketGame) -> Unit,
    viewModel: WishlistViewModel = viewModel()
) {
    val context = LocalContext.current
    val steamId = UserPrefs.getSteamId(context)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val statusBarDp = statusBarHeightDp()
    val topBarHeightDp = statusBarDp + 56.dp
    val listState = rememberLazyListState()
    var sortMode by remember { mutableIntStateOf(0) }
    var showSortOptions by remember { mutableStateOf(false) }
    val sortLabels = listOf(
        stringResource(R.string.wishlist_sort_name),
        stringResource(R.string.wishlist_sort_price),
        stringResource(R.string.wishlist_sort_discount)
    )
    val games = uiState.games.orEmpty()
    val sortedGames = remember(games, sortMode, context) {
        when (sortMode) {
            1 -> games.sortedBy { it.priceVal }
            2 -> games.sortedByDescending { it.discount }
            else -> games.sortedBy {
                (GameNames.getName(context, it.id) ?: it.name).lowercase(Locale.getDefault())
            }
        }
    }
    val errorMessage = uiState.error?.let { (resId, arg) ->
        if (arg == null) stringResource(resId) else stringResource(resId, arg)
    }

    LaunchedEffect(steamId) { viewModel.load(steamId) }
    LaunchedEffect(sortMode) { listState.scrollToItem(0) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.isLoading && uiState.games == null -> {
                            SpecialsSkeleton(topBarHeightDp)
                        }
                        errorMessage != null && uiState.games == null -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = errorMessage,
                                    fontSize = DesignTokens.TextSubtitle.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(
                                        alpha = DesignTokens.OpacityBody
                                    )
                                )
                                TextButton(
                                    text = stringResource(R.string.general_retry),
                                    onClick = { viewModel.load(steamId, force = true) }
                                )
                            }
                        }
                        sortedGames.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.wishlist_empty),
                                fontSize = DesignTokens.TextSubtitle.sp,
                                color = MiuixTheme.colorScheme.onSurface.copy(
                                    alpha = DesignTokens.OpacityBody
                                ),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = topBarHeightDp + DesignTokens.SpaceSm,
                                    bottom = DesignTokens.SpaceXl
                                )
                            ) {
                                items(sortedGames, key = { it.id }) { game ->
                                    MarketGameItem(
                                        game = game,
                                        modifier = Modifier.animateItem(),
                                        onClick = { onOpenGame(game) }
                                    )
                                }
                            }
                        }
                    }
                }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .scrollLinkedTopBar(listState, topBarHeightDp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarDp + 4.dp, end = 12.dp, bottom = 4.dp)
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
                        text = stringResource(R.string.wishlist_title),
                        fontSize = DesignTokens.TextHeadline.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(start = DesignTokens.SpaceXs)
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                            .motionClickable { showSortOptions = !showSortOptions }
                            .padding(
                                horizontal = DesignTokens.SpaceMd,
                                vertical = DesignTokens.SpaceSm
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sortLabels[sortMode],
                            fontSize = DesignTokens.TextBody1.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(DesignTokens.SpaceSm))
                        DropdownArrowEndAction(
                            actionColor = if (showSortOptions) {
                                DesignTokens.AccentBlue
                            } else {
                                MiuixTheme.colorScheme.onSurface.copy(
                                    alpha = DesignTokens.OpacityBody
                                )
                            }
                        )
                    }
                }
            }

            if (showSortOptions) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DesignTokens.ScrimDark)
                        .clickable { showSortOptions = false }
                )
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = statusBarDp + 56.dp, end = 12.dp)
                        .width(140.dp)
                ) {
                    Column {
                        sortLabels.forEachIndexed { index, label ->
                            val selected = sortMode == index
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .motionClickable {
                                        sortMode = index
                                        showSortOptions = false
                                    }
                                    .padding(
                                        start = 16.dp,
                                        end = 8.dp,
                                        top = 12.dp,
                                        bottom = 12.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = DesignTokens.TextBody1.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) {
                                        DesignTokens.AccentBlue
                                    } else {
                                        MiuixTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                Image(
                                    imageVector = MiuixIcons.Basic.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(DesignTokens.IconMd),
                                    colorFilter = ColorFilter.tint(
                                        if (selected) DesignTokens.AccentBlue else Color.Transparent
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
