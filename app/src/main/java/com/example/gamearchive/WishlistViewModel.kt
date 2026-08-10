package com.example.gamearchive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException

data class WishlistUiState(
    val games: List<MarketGame>? = null,
    val isLoading: Boolean = false,
    val error: Pair<Int, String?>? = null
)

internal interface WishlistDataSource {
    suspend fun wishlist(steamId: String): List<WishlistItem>
    suspend fun storeItems(inputJson: String): List<StoreBrowseItem>
}

private class NetworkWishlistDataSource : WishlistDataSource {
    override suspend fun wishlist(steamId: String): List<WishlistItem> =
        GameArchiveApp.apiService.getWishlist(steamId).response?.items.orEmpty()

    override suspend fun storeItems(inputJson: String): List<StoreBrowseItem> =
        GameArchiveApp.apiService.getStoreItems(inputJson).response?.store_items.orEmpty()
}

class WishlistViewModel internal constructor(
    private val dataSource: WishlistDataSource = NetworkWishlistDataSource()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WishlistUiState())
    val uiState: StateFlow<WishlistUiState> = _uiState.asStateFlow()

    private var loadedSteamId: String? = null

    fun load(steamId: String, force: Boolean = false) {
        if (steamId.isBlank()) {
            _uiState.update { it.copy(error = Pair(R.string.general_please_login, null)) }
            return
        }
        if (!force && loadedSteamId == steamId && _uiState.value.games != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val games = fetchWishlistGames(steamId)
                loadedSteamId = steamId
                _uiState.update { it.copy(games = games) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = when (error) {
                    is HttpException -> Pair(R.string.general_network_http, error.code().toString())
                    else -> Pair(R.string.general_error, error.message)
                }
                _uiState.update { it.copy(error = message) }
                android.util.Log.e("WishlistLoad", "Steam wishlist load failed", error)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun fetchWishlistGames(steamId: String): List<MarketGame> = supervisorScope {
        val appIds = dataSource.wishlist(steamId)
            .asSequence()
            .map { it.appid }
            .filter { it > 0 }
            .distinct()
            .toList()
        if (appIds.isEmpty()) return@supervisorScope emptyList()

        appIds.chunked(50)
            .map { batch -> async { dataSource.storeItems(createStoreRequest(batch)) } }
            .awaitAll()
            .flatten()
            .distinctBy { it.appid }
            .mapNotNull(::toMarketGame)
    }

    private fun createStoreRequest(appIds: List<Int>): String = JSONObject()
        .put(
            "ids",
            JSONArray().apply {
                appIds.forEach { put(JSONObject().put("appid", it)) }
            }
        )
        .put(
            "context",
            JSONObject()
                .put("language", LocaleHelper.currentApiLanguage)
                .put("country_code", "CN")
                .put("steam_realm", 1)
        )
        .put(
            "data_request",
            JSONObject()
                .put("include_assets", true)
                .put("include_reviews", true)
                .put("include_all_purchase_options", true)
        )
        .toString()

    private fun toMarketGame(item: StoreBrowseItem): MarketGame? {
        val name = item.name?.takeIf(String::isNotBlank) ?: return null
        val option = item.best_purchase_option
        val backupUrl = buildAssetUrl(item.assets, item.assets?.header)
        return MarketGame(
            id = item.appid,
            name = name,
            imgUrl = "https://cdn.cloudflare.steamstatic.com/steam/apps/${item.appid}/header.jpg",
            finalPriceStr = option?.formatted_final_price.orEmpty(),
            originalPriceStr = option?.formatted_original_price,
            discount = option?.discount_pct ?: 0,
            backupImgUrl = backupUrl,
            priceVal = option?.final_price_in_cents?.toDoubleOrNull()?.div(100.0)
                ?: Double.POSITIVE_INFINITY,
            reviewScore = item.reviews?.summary_filtered?.percent_positive ?: -1
        )
    }

    private fun buildAssetUrl(assets: StoreBrowseAssets?, file: String?): String? {
        val format = assets?.asset_url_format ?: return null
        val filename = file?.takeIf(String::isNotBlank) ?: return null
        if (!format.contains("\${FILENAME}")) return null
        return "https://shared.cloudflare.steamstatic.com/store_item_assets/" +
            format.replace("\${FILENAME}", filename)
    }
}
