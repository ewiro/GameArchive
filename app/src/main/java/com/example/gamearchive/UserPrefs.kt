package com.example.gamearchive

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object UserPrefs {
    // SharedPreferences 文件名
    const val PREF_NAME = "steam_user_data"

    // 数据键名定义
    private const val KEY_API_KEY = "api_key"
    private const val KEY_STEAM_ID = "steam_id"
    private const val KEY_CUSTOM_BG = "custom_bg_url"
    private const val KEY_CUSTOM_FRAME = "custom_frame_url"
    private const val KEY_CUSTOM_AVATAR = "custom_avatar_url"
    private const val KEY_SHOW_PROFILE = "show_profile_card"

    // 获取 SharedPreferences 实例的辅助方法
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // --- 个性化设置存取方法 ---

    // 保存自定义背景图片链接
    fun saveCustomBgUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_BG, url.trim()).apply()
    }

    // 读取自定义背景图片链接
    fun getCustomBgUrl(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_BG, "") ?: ""
    }

    // 保存自定义头像挂件链接
    fun saveCustomFrameUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_FRAME, url.trim()).apply()
    }

    // 读取自定义头像挂件链接
    fun getCustomFrameUrl(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_FRAME, "") ?: ""
    }

    // 保存自定义头像链接
    fun saveCustomAvatarUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_AVATAR, url.trim()).apply()
    }

    // 读取自定义头像链接
    fun getCustomAvatarUrl(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_AVATAR, "") ?: ""
    }

    // 保存是否显示资料卡片
    fun saveShowProfile(context: Context, enable: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_PROFILE, enable).apply()
    }

    // 读取是否显示 (默认 false，即默认隐藏，按你的要求)
    fun isShowProfile(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_PROFILE, false)
    }

    // --- 账号凭证存取方法 ---

    // 保存登录凭证 (API Key 和 Steam ID)
    fun saveCredentials(context: Context, apiKey: String, steamId: String) {
        getPrefs(context).edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_STEAM_ID, steamId.trim())
            .apply()
    }

    // 获取保存的 API Key
    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    // 获取保存的 Steam ID
    fun getSteamId(context: Context): String {
        return getPrefs(context).getString(KEY_STEAM_ID, "") ?: ""
    }

    // 检查用户是否已登录 (判断 Key 和 ID 是否都存在)
    fun isLoggedIn(context: Context): Boolean {
        return getApiKey(context).isNotEmpty() && getSteamId(context).isNotEmpty()
    }

    // 退出登录，清空所有保存的数据
    fun logout(context: Context) {
        getPrefs(context).edit().clear().apply()
        GameArchiveApp.clearAuthenticatedBgmService()
    }

    // ── 多账号管理 ──
    private const val KEY_ADDITIONAL_ACCOUNTS = "additional_accounts"
    private const val KEY_STEAM_NICKNAMES = "steam_nicknames"

    /** 获取额外账号列表 (steamId, apiKey) */
    fun getAdditionalAccounts(context: Context): List<Pair<String, String>> {
        val json = getPrefs(context).getString(KEY_ADDITIONAL_ACCOUNTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Pair(obj.getString("steamId"), obj.getString("apiKey"))
            }
        } catch (_: Exception) { emptyList() }
    }

    /** 获取所有账号（主账号 + 额外） */
    fun getAllAccounts(context: Context): List<Pair<String, String>> {
        val primary = Pair(getSteamId(context), getApiKey(context))
        if (primary.first.isEmpty() || primary.second.isEmpty()) return getAdditionalAccounts(context)
        return listOf(primary) + getAdditionalAccounts(context)
    }

    /** 添加额外账号，重复steamId返回false */
    fun addAccount(
        context: Context,
        steamId: String,
        apiKey: String
    ): Boolean {
        val all = getAllAccounts(context)
        if (all.any { it.first == steamId }) return false
        val existing = getAdditionalAccounts(context).toMutableList()
        existing.add(Pair(steamId, apiKey))
        saveAdditionalAccounts(context, existing)
        return true
    }

    /** 删除额外账号 */
    fun removeAccount(context: Context, index: Int) {
        val existing = getAdditionalAccounts(context).toMutableList()
        if (index in existing.indices) {
            val removedSteamId = existing.removeAt(index).first
            saveAdditionalAccounts(context, existing)
            removeSteamNickname(context, removedSteamId)
        }
    }

    private fun saveAdditionalAccounts(context: Context, accounts: List<Pair<String, String>>) {
        val arr = JSONArray()
        for (acc in accounts) {
            val obj = JSONObject()
            obj.put("steamId", acc.first)
            obj.put("apiKey", acc.second)
            arr.put(obj)
        }
        getPrefs(context).edit().putString(KEY_ADDITIONAL_ACCOUNTS, arr.toString()).apply()
    }

    fun getStoredSteamNickname(context: Context, steamId: String): String {
        val names = runCatching {
            JSONObject(getPrefs(context).getString(KEY_STEAM_NICKNAMES, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        return names.optString(steamId)
    }

    fun getSteamNickname(context: Context, steamId: String): String {
        return getStoredSteamNickname(context, steamId).ifBlank { steamId }
    }

    fun saveSteamNickname(context: Context, steamId: String, nickname: String) {
        if (steamId.isBlank() || nickname.isBlank()) return
        val names = runCatching {
            JSONObject(getPrefs(context).getString(KEY_STEAM_NICKNAMES, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        names.put(steamId, nickname.trim())
        getPrefs(context).edit().putString(KEY_STEAM_NICKNAMES, names.toString()).apply()
    }

    private fun removeSteamNickname(context: Context, steamId: String) {
        val names = runCatching {
            JSONObject(getPrefs(context).getString(KEY_STEAM_NICKNAMES, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        names.remove(steamId)
        getPrefs(context).edit().putString(KEY_STEAM_NICKNAMES, names.toString()).apply()
    }

    // ── Bangumi 用户名 ──
    private const val KEY_BANGUMI_USERNAME = "bangumi_username"

    fun getBangumiUsername(context: Context): String {
        return getPrefs(context).getString(KEY_BANGUMI_USERNAME, "") ?: ""
    }

    fun setBangumiUsername(context: Context, username: String) {
        getPrefs(context).edit().putString(KEY_BANGUMI_USERNAME, username.trim()).apply()
    }

    // ── Bangumi 评分展示模式：0=展示评分, 1=仅我的评分, 2=不展示 ──
    private const val KEY_BANGUMI_RATING_MODE = "bangumi_rating_mode"

    fun getBangumiRatingMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_BANGUMI_RATING_MODE, 0)
    }

    fun setBangumiRatingMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_BANGUMI_RATING_MODE, mode).apply()
    }

    // ── Bangumi OAuth Token ──
    private const val KEY_BANGUMI_ACCESS_TOKEN = "bangumi_access_token"
    private const val KEY_BANGUMI_REFRESH_TOKEN = "bangumi_refresh_token"
    private const val KEY_BANGUMI_USER_ID = "bangumi_user_id"

    fun getBangumiAccessToken(context: Context): String {
        return getPrefs(context).getString(KEY_BANGUMI_ACCESS_TOKEN, "") ?: ""
    }
    fun setBangumiAccessToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_BANGUMI_ACCESS_TOKEN, token).apply()
    }
    fun getBangumiRefreshToken(context: Context): String {
        return getPrefs(context).getString(KEY_BANGUMI_REFRESH_TOKEN, "") ?: ""
    }
    fun setBangumiRefreshToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_BANGUMI_REFRESH_TOKEN, token).apply()
    }
    fun getBangumiUserId(context: Context): Int {
        return getPrefs(context).getInt(KEY_BANGUMI_USER_ID, 0)
    }
    fun setBangumiUserId(context: Context, id: Int) {
        getPrefs(context).edit().putInt(KEY_BANGUMI_USER_ID, id).apply()
    }
    fun isBangumiAuthorized(context: Context): Boolean {
        return getBangumiAccessToken(context).isNotEmpty()
    }
    fun clearBangumiToken(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_BANGUMI_ACCESS_TOKEN)
            .remove(KEY_BANGUMI_REFRESH_TOKEN)
            .remove(KEY_BANGUMI_USER_ID)
            .remove(KEY_BANGUMI_USERNAME)
            .apply()
        GameArchiveApp.clearAuthenticatedBgmService()
    }
}
