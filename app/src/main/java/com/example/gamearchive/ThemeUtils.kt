package com.example.gamearchive

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate

object ThemeUtils {
    // SharedPreferences 文件名
    private const val PREF_NAME = "app_theme_prefs"

    // 设置项键名定义
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_PURE_BLACK = "pure_black"
    private const val KEY_ENABLE_GROUPING = "enable_grouping"
    private const val KEY_SORT_MODE = "sort_mode"
    private const val KEY_GROUP_RECENT = "group_recent"
    private const val KEY_LANGUAGE = "language"

    // 标记设置是否发生变化，用于通知 Activity 重启
    var isChanged = false

    // MIUIX 经典蓝 (强调色，用于 SwipeRefresh 等需要代码取色的场景)
    const val ACCENT_BLUE = 0xFF3482FF.toInt()

    // 将用户设置的主题应用到 Activity
    fun applyTheme(activity: Activity) {
        val prefs = getPrefs(activity)

        // 1. 设置夜间模式 (跟随系统/浅色/深色)
        val mode = prefs.getInt(KEY_THEME_MODE, 2)
        val nightMode = when (mode) {
            0 -> AppCompatDelegate.MODE_NIGHT_NO
            1 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)

        // 2. 纯黑模式 (仅在深色模式下生效)
        val isPureBlack = prefs.getBoolean(KEY_PURE_BLACK, false)
        val isNight = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (isPureBlack && isNight) {
            activity.window.decorView.setBackgroundColor(Color.BLACK)
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // --- 设置项存取方法 ---

    // 保存主题模式
    fun saveThemeMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
        isChanged = true
    }
    fun getThemeMode(context: Context) = getPrefs(context).getInt(KEY_THEME_MODE, 2)

    // 保存纯黑模式开关
    fun savePureBlack(context: Context, enable: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PURE_BLACK, enable).apply()
        isChanged = true
    }
    fun isPureBlackEnabled(context: Context) = getPrefs(context).getBoolean(KEY_PURE_BLACK, false)

    // 保存分组开关 (用于库存列表)
    fun saveGrouping(context: Context, enable: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLE_GROUPING, enable).apply()
        isChanged = true
    }
    fun isGroupingEnabled(context: Context) = getPrefs(context).getBoolean(KEY_ENABLE_GROUPING, false)

    // 保存排序模式 (用于库存列表)
    fun saveSortMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_SORT_MODE, mode).apply()
        isChanged = true
    }
    fun getSortMode(context: Context) = getPrefs(context).getInt(KEY_SORT_MODE, 0)

    // 保存近期分组开关 (用于库存列表)
    fun saveGroupRecent(context: Context, enable: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_GROUP_RECENT, enable).apply()
        isChanged = true
    }
    fun isGroupRecentEnabled(context: Context) = getPrefs(context).getBoolean(KEY_GROUP_RECENT, false)

    // 语言设置：0=跟随系统 1=中文 2=英文
    fun saveLanguage(context: Context, lang: Int) {
        getPrefs(context).edit().putInt(KEY_LANGUAGE, lang).apply()
        isChanged = true
    }
    fun getLanguage(context: Context) = getPrefs(context).getInt(KEY_LANGUAGE, 0)
}
