package com.example.gamearchive

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

// ── 封面 URL 构建（含 BF6 + 缓存回退 + 90天过期） ──
internal const val BF6_APPID = 2807960
private const val HEADER_CACHE_TTL_MS = 90L * 24 * 3600 * 1000  // 90 天
private const val PORTRAIT_CACHE_PREF = "steam_portrait_cache"

internal fun buildHeaderUrl(context: Context, appId: Int): Any {
    if (appId == BF6_APPID) return R.drawable.bf6_header
    val cache = context.getSharedPreferences("steam_header_cache", Context.MODE_PRIVATE)
    val cached = readCacheWithExpiry(cache, "header_$appId", HEADER_CACHE_TTL_MS)
    return cached ?: "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
}

internal fun readCachedPortraitUrl(context: Context, appId: Int): String? {
    val cache = context.getSharedPreferences(PORTRAIT_CACHE_PREF, Context.MODE_PRIVATE)
    return readCacheWithExpiry(cache, "portrait_$appId", HEADER_CACHE_TTL_MS)
}

internal fun cachePortraitUrl(context: Context, appId: Int, url: String) {
    context.getSharedPreferences(PORTRAIT_CACHE_PREF, Context.MODE_PRIVATE)
        .edit {
            putString("portrait_$appId", "$url|${System.currentTimeMillis()}")
        }
}

internal suspend fun resolveSteamPortraitUrl(appId: Int): String? {
    return resolveSteamPortraitUrl(appId, "CN")
        ?: resolveSteamPortraitUrl(appId, "US")
}

private suspend fun resolveSteamPortraitUrl(appId: Int, countryCode: String): String? {
    val request = JSONObject()
        .put("ids", JSONArray().put(JSONObject().put("appid", appId)))
        .put(
            "context",
            JSONObject()
                .put("language", LocaleHelper.currentApiLanguage)
                .put("country_code", countryCode)
                .put("steam_realm", 1)
        )
        .put("data_request", JSONObject().put("include_assets", true))
        .toString()
    val assets = GameArchiveApp.apiService.getStoreItems(request)
        .response
        ?.store_items
        ?.firstOrNull { it.appid == appId }
        ?.assets
        ?: return null
    val file = assets.library_capsule_2x
        ?.takeIf(String::isNotBlank)
        ?: assets.library_capsule?.takeIf(String::isNotBlank)
        ?: return null
    val path = assets.asset_url_format
        ?.takeIf { it.contains("\${FILENAME}") }
        ?.replace("\${FILENAME}", file)
        ?: return null
    return "https://shared.cloudflare.steamstatic.com/store_item_assets/$path"
}

// 通用缓存读取（含过期检查）。格式 "value|timestamp"，过期返回 null
private fun readCacheWithExpiry(prefs: android.content.SharedPreferences, key: String, ttlMs: Long): String? {
    val entry = prefs.getString(key, null) ?: return null
    val parts = entry.split("|", limit = 2)
    if (parts.size < 2) {
        prefs.edit {remove(key)}
        return null
    }
    val ts = parts[1].toLongOrNull() ?: 0L
    if (System.currentTimeMillis() - ts > ttlMs) {
        prefs.edit {remove(key)}
        return null
    }
    return parts[0]
}

// ── 特惠页封面白名单（standardImgUrl 失效的游戏，改用 backupImgUrl）──
private const val SPECIALS_WHITELIST_PREF = "specials_cover_whitelist"

internal fun isSpecialsCoverWhitelisted(context: Context, appId: Int): Boolean {
    val prefs = context.getSharedPreferences(SPECIALS_WHITELIST_PREF, Context.MODE_PRIVATE)
    return prefs.getBoolean("w_$appId", false)
}

internal fun addToSpecialsWhitelist(context: Context, appId: Int) {
    val prefs = context.getSharedPreferences(SPECIALS_WHITELIST_PREF, Context.MODE_PRIVATE)
    prefs.edit {putBoolean("w_$appId", true)}
}
