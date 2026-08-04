package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val COLLECTION_PAGE_SIZE = 100
private val COLLECTION_TITLE_REGEX = Regex("<span class=\"title\">(.*?)</span>")
private val COLLECTION_APP_ID_REGEX = Regex("data-ds-appid=\"([0-9,]+)\"")
private val COLLECTION_IMAGE_REGEX = Regex("src=\"(https://[^\"]+?\\.jpg[^\"]*)\"")
private val COLLECTION_PRICE_REGEX = Regex("discount_final_price[^\"]*\">([^<]+)</div>")
private val COLLECTION_ORIGINAL_PRICE_REGEX =
    Regex("discount_original_price\">([^<]+)</div>")
private val COLLECTION_DISCOUNT_REGEX = Regex("-([0-9]+)%")
private val COLLECTION_TOOLTIP_REGEX = Regex("data-tooltip-html=\"([^\"]+)\"")
private val COLLECTION_SCORE_REGEX = Regex("([0-9]{1,3})%")

private data class GameCollectionEntry(
    val appId: Int,
    val name: String,
    val displayName: String,
    val imageUrl: String,
    val price: String,
    val ownedGame: GameInfo? = null,
    val marketGame: MarketGame? = null
)

@Suppress("DEPRECATION")
class GameCollectionActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeUtils.applyTheme(this)

        val mode = intent.getStringExtra(EXTRA_MODE).orEmpty()
        val value = intent.getStringExtra(EXTRA_VALUE).orEmpty()
        setContent {
            MiuixThemeForApp {
                GameCollectionScreen(mode = mode, value = value, onBack = { finish() })
            }
        }
    }

    companion object {
        const val MODE_DEVELOPER = "developer"
        const val MODE_TAG = "tag"
        private const val EXTRA_MODE = "COLLECTION_MODE"
        private const val EXTRA_VALUE = "COLLECTION_VALUE"

        fun createIntent(context: Context, mode: String, value: String): Intent =
            Intent(context, GameCollectionActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_VALUE, value)
            }
    }
}

@Composable
private fun GameCollectionScreen(mode: String, value: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModel = viewModel()
    val showPlaytimeBackground = ThemeUtils.isPlaytimeBadgeBackgroundEnabled(context)
    val usePlaytimeBadgeTextColor = ThemeUtils.isPlaytimeBadgeTextColorEnabled(context)
    val title = when (mode) {
        GameCollectionActivity.MODE_DEVELOPER ->
            context.getString(R.string.game_collection_developer_title, value)
        GameCollectionActivity.MODE_TAG ->
            context.getString(R.string.game_collection_tag_title, value)
        else -> context.getString(R.string.game_collection_title)
    }
    var entries by remember(mode, value) { mutableStateOf<List<GameCollectionEntry>>(emptyList()) }
    var isLoading by remember(mode, value) { mutableStateOf(true) }
    var loadFailed by remember(mode, value) { mutableStateOf(false) }
    var loadRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(mode, value, loadRevision) {
        isLoading = true
        loadFailed = false
        val result = runCatching {
            when (mode) {
                GameCollectionActivity.MODE_DEVELOPER ->
                    loadDeveloperGames(context.applicationContext, value)
                GameCollectionActivity.MODE_TAG -> loadTaggedGames(context.applicationContext, value)
                else -> error("Unknown collection mode")
            }
        }
        entries = result.getOrDefault(emptyList())
        loadFailed = result.isFailure
        isLoading = false
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
                    text = title,
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
                label = "game_collection_loading",
                modifier = Modifier.weight(1f)
            ) { loading ->
                if (loading) {
                    BangumiSearchLoadingSkeleton()
                } else if (loadFailed) {
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
                } else if (entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = context.getString(R.string.general_no_data),
                            color = MiuixTheme.colorScheme.onSurface.copy(
                                alpha = DesignTokens.OpacityBody
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            bottom = DesignTokens.SpaceXxl
                        )
                    ) {
                        items(entries, key = { it.appId }) { entry ->
                            val openDetail = {
                                context.startActivity(
                                    Intent(context, DetailActivity::class.java).apply {
                                        putExtra("APP_ID", entry.appId)
                                        putExtra("APP_NAME", entry.name.ifBlank { entry.displayName })
                                        putExtra("APP_PRICE", entry.price)
                                    }
                                )
                            }
                            val ownedGame = entry.ownedGame
                            if (ownedGame != null) {
                                GameItem(
                                    game = ownedGame,
                                    price = "",
                                    customName = entry.displayName.takeIf {
                                        it.isNotBlank() && it != ownedGame.name
                                    },
                                    markRes = GameMarks.getMark(context, entry.appId),
                                    viewModel = libraryViewModel,
                                    showPlaytimeBackground = showPlaytimeBackground,
                                    usePlaytimeBadgeTextColor = usePlaytimeBadgeTextColor,
                                    onClick = openDetail
                                )
                            } else {
                                entry.marketGame?.let { marketGame ->
                                    MarketGameItem(
                                        game = marketGame,
                                        onClick = openDetail
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

private suspend fun loadDeveloperGames(
    context: Context,
    developer: String
): List<GameCollectionEntry> =
    withContext(Dispatchers.IO) {
        if (developer.isBlank()) return@withContext emptyList()
        val encodedDeveloper = URLEncoder.encode(
            developer,
            StandardCharsets.UTF_8.name()
        )
        val allEntries = LinkedHashMap<Int, GameCollectionEntry>()
        var start = 0
        var totalCount = Int.MAX_VALUE
        while (start < totalCount) {
            val url = "${AppConfig.PROXY_URL}search/results/?query&start=$start" +
                "&count=$COLLECTION_PAGE_SIZE&dynamic_data=&developer=$encodedDeveloper" +
                "&infinite=1&l=${LocaleHelper.currentApiLanguage}&cc=cn&category1=998"
            val request = Request.Builder().url(url).build()
            val json = GameArchiveApp.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                JSONObject(response.body?.string().orEmpty())
            }
            if (json.optInt("success", 0) != 1) error("Steam search failed")
            totalCount = json.optInt("total_count", 0)
            parseDeveloperSearchHtml(json.optString("results_html", ""))
                .forEach { allEntries.putIfAbsent(it.appId, it) }
            start += COLLECTION_PAGE_SIZE
        }
        val ownedGames = loadOwnedGamesForCollection(context)
        val customNames = GameNames.getAllNames(context)
        allEntries.values.map { entry ->
            val ownedGame = ownedGames[entry.appId]
            entry.copy(
                name = entry.name.ifBlank { ownedGame?.name.orEmpty() },
                displayName = customNames[entry.appId].orEmpty().ifBlank { entry.name },
                ownedGame = ownedGame,
                marketGame = entry.marketGame.takeIf { ownedGame == null }
            )
        }
    }

private suspend fun loadTaggedGames(context: Context, tag: String): List<GameCollectionEntry> =
    withContext(Dispatchers.IO) {
        val appIds = GameTags.getGameIdsForTag(context, tag)
        if (appIds.isEmpty()) return@withContext emptyList()
        val customNames = GameNames.getAllNames(context)
        val requestLimit = Semaphore(6)
        val (ownedGames, details) = supervisorScope {
            val ownedDeferred = async { loadOwnedGamesForCollection(context) }
            val detailsDeferred = async {
                appIds.map { appId ->
                    async {
                        requestLimit.withPermit {
                            val data = runCatching {
                                GameArchiveApp.apiService.getGamePrices(
                                    ids = appId.toString(),
                                    f = "basic,price_overview",
                                    l = LocaleHelper.currentApiLanguage
                                )[appId.toString()]?.data
                            }.getOrNull()
                            appId to data
                        }
                    }
                }.awaitAll().mapNotNull { (appId, data) ->
                    data?.let { appId to it }
                }.toMap()
            }
            ownedDeferred.await() to detailsDeferred.await()
        }
        val reviewScores = supervisorScope {
            appIds.filterNot { ownedGames.containsKey(it) }.map { appId ->
                async {
                    requestLimit.withPermit {
                        val score = runCatching {
                            val summary = GameArchiveApp.apiService.getGameReviews(
                                id = appId,
                                l = LocaleHelper.currentApiLanguage,
                                count = 1
                            ).query_summary
                            if (summary != null && summary.total_reviews > 0) {
                                (summary.total_positive.toDouble() / summary.total_reviews * 100).toInt()
                            } else {
                                -1
                            }
                        }.getOrDefault(-1)
                        appId to score
                    }
                }
            }.awaitAll().toMap()
        }
        appIds.map { appId ->
            val data = details[appId]
            val ownedGame = ownedGames[appId]
            val originalName = data?.name.orEmpty().ifBlank { ownedGame?.name.orEmpty() }
            val displayName = customNames[appId].orEmpty().ifBlank { originalName }
            val standardImage = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
            val priceOverview = data?.price_overview
            val finalPrice = priceOverview?.final_formatted.orEmpty().ifBlank {
                context.getString(R.string.detail_unknown)
            }
            val marketGame = if (ownedGame == null) {
                MarketGame(
                    id = appId,
                    name = originalName.ifBlank {
                        context.getString(R.string.game_collection_fallback_name, appId)
                    },
                    imgUrl = standardImage,
                    finalPriceStr = finalPrice,
                    originalPriceStr = priceOverview?.initial_formatted?.takeIf {
                        priceOverview.discount_percent.orZero() > 0
                    },
                    discount = priceOverview?.discount_percent.orZero(),
                    backupImgUrl = data?.header_image,
                    priceVal = (priceOverview?.final ?: 0) / 100.0,
                    reviewScore = reviewScores[appId] ?: -1
                )
            } else {
                null
            }
            GameCollectionEntry(
                appId = appId,
                name = originalName,
                displayName = displayName,
                imageUrl = data?.header_image.orEmpty().ifBlank { standardImage },
                price = finalPrice,
                ownedGame = ownedGame,
                marketGame = marketGame
            )
        }.sortedBy { it.displayName.ifBlank { it.name }.lowercase() }
    }

private suspend fun loadOwnedGamesForCollection(context: Context): Map<Int, GameInfo> =
    supervisorScope {
        val accounts = UserPrefs.getAllAccounts(context)
        if (accounts.isEmpty()) return@supervisorScope emptyMap()
        val results = accounts.map { (steamId, apiKey) ->
            async {
                runCatching {
                    GameArchiveApp.apiService.getOwnedGames(apiKey, steamId).response.games
                }
            }
        }.awaitAll()
        if (results.none { it.isSuccess }) {
            throw results.firstNotNullOf { it.exceptionOrNull() }
        }
        buildMap {
            results.forEach { result ->
                result.getOrNull().orEmpty().forEach { game ->
                    putIfAbsent(game.appid, game)
                }
            }
        }
    }

private fun Int?.orZero(): Int = this ?: 0

private fun parseDeveloperSearchHtml(html: String): List<GameCollectionEntry> {
    val entries = mutableListOf<GameCollectionEntry>()
    html.split("<a href=").forEach { row ->
        val appId = COLLECTION_APP_ID_REGEX.find(row)
            ?.groupValues?.getOrNull(1)
            ?.substringBefore(',')
            ?.toIntOrNull()
            ?: return@forEach
        val rawName = COLLECTION_TITLE_REGEX.find(row)?.groupValues?.getOrNull(1).orEmpty()
        val name = decodeHtml(rawName)
        if (name.isBlank()) return@forEach
        val imageUrl = COLLECTION_IMAGE_REGEX.find(row)
            ?.groupValues?.getOrNull(1)
            ?.replace("&amp;", "&")
            .orEmpty()
            .ifBlank { "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg" }
        val price = decodeHtml(
            COLLECTION_PRICE_REGEX.find(row)?.groupValues?.getOrNull(1).orEmpty()
        )
        val originalPrice = decodeHtml(
            COLLECTION_ORIGINAL_PRICE_REGEX.find(row)
                ?.groupValues?.getOrNull(1).orEmpty()
        ).ifBlank { null }
        val discount = COLLECTION_DISCOUNT_REGEX.find(row)
            ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val reviewScore = COLLECTION_TOOLTIP_REGEX.find(row)
            ?.groupValues?.getOrNull(1)
            ?.let { COLLECTION_SCORE_REGEX.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?: -1
        val standardImage =
            "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
        entries += GameCollectionEntry(
            appId = appId,
            name = name,
            displayName = name,
            imageUrl = imageUrl,
            price = price,
            marketGame = MarketGame(
                id = appId,
                name = name,
                imgUrl = standardImage,
                finalPriceStr = price,
                originalPriceStr = originalPrice,
                discount = discount,
                backupImgUrl = imageUrl,
                reviewScore = reviewScore
            )
        )
    }
    return entries
}

private fun decodeHtml(value: String): String =
    Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().trim()
