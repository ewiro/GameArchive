package com.example.gamearchive

import android.content.Context
import android.content.SharedPreferences

object UserPrefs {
    // SharedPreferences 文件名
    private const val PREF_NAME = "steam_user_data"

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
    }
}