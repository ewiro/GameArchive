package com.example.gamearchive

internal data class SteamSearchRow(
    val appId: Int,
    val title: String,
    val imageUrl: String,
    val finalPrice: String,
    val originalPrice: String,
    val discount: Int,
    val priceValue: Double,
    val reviewScore: Int
)

private val STEAM_TITLE_REGEX = Regex("<span class=\"title\">(.*?)</span>")
private val STEAM_APP_ID_REGEX = Regex("data-ds-appid=\"([0-9,]+)\"")
private val STEAM_IMAGE_REGEX = Regex("src=\"(https://[^\"]+?\\.jpg[^\"]*)\"")
private val STEAM_DISCOUNT_REGEX = Regex("-([0-9]+)%")
private val STEAM_FINAL_PRICE_REGEX =
    Regex("discount_final_price[^\"]*\">([^<]+)</div>")
private val STEAM_ORIGINAL_PRICE_REGEX =
    Regex("discount_original_price\">([^<]+)</div>")
private val STEAM_PRICE_NUMBER_REGEX = Regex("[^0-9.]")
private val STEAM_TOOLTIP_REGEX = Regex("data-tooltip-html=\"([^\"]+)\"")
private val STEAM_SCORE_REGEX = Regex("([0-9]{1,3})%")

internal fun parseSteamSearchRows(html: String): List<SteamSearchRow> =
    html.split("<a href=").mapNotNull { row ->
        val appId = STEAM_APP_ID_REGEX.find(row)
            ?.groupValues?.getOrNull(1)
            ?.substringBefore(',')
            ?.toIntOrNull()
            ?: return@mapNotNull null
        val title = STEAM_TITLE_REGEX.find(row)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return@mapNotNull null
        val imageUrl = STEAM_IMAGE_REGEX.find(row)
            ?.groupValues?.getOrNull(1)
            .orEmpty()
        val finalPrice = STEAM_FINAL_PRICE_REGEX.find(row)
            ?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val originalPrice = STEAM_ORIGINAL_PRICE_REGEX.find(row)
            ?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val discount = STEAM_DISCOUNT_REGEX.find(row)
            ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val priceValue = finalPrice.replace(STEAM_PRICE_NUMBER_REGEX, "")
            .toDoubleOrNull() ?: 0.0
        val reviewScore = STEAM_TOOLTIP_REGEX.find(row)
            ?.groupValues?.getOrNull(1)
            ?.let { STEAM_SCORE_REGEX.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?: -1
        SteamSearchRow(
            appId = appId,
            title = title,
            imageUrl = imageUrl,
            finalPrice = finalPrice,
            originalPrice = originalPrice,
            discount = discount,
            priceValue = priceValue,
            reviewScore = reviewScore
        )
    }
