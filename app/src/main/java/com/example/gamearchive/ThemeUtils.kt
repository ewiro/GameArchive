package com.example.gamearchive

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

object ThemeUtils {
    // SharedPreferences 文件名（供 DataBackup 引用）
    const val PREF_NAME = "app_theme_prefs"

    // 设置项键名定义
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ENABLE_GROUPING = "enable_grouping"
    private const val KEY_SORT_MODE = "sort_mode"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_SHOW_SPECIALS = "show_specials"

    // 标记设置是否发生变化，用于通知 Activity 重启
    var isChanged = false

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

        // 2. 设置状态栏图标颜色
        applyStatusBarAppearance(activity, mode)
    }

    /** 仅更新状态栏图标颜色（用于 onResume 等不再走 onCreate 的场景） */
    fun applyStatusBarAppearance(activity: Activity) {
        val mode = getPrefs(activity).getInt(KEY_THEME_MODE, 2)
        applyStatusBarAppearance(activity, mode)
    }

    private fun applyStatusBarAppearance(activity: Activity, themeMode: Int) {
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        insetsController.isAppearanceLightStatusBars = when (themeMode) {
            0 -> true   // 浅色模式：深色状态栏图标
            1 -> false  // 深色模式：浅色状态栏图标
            else -> {   // 跟随系统：检测当前系统是否深色
                val nightModeFlags = activity.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags != android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
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

    // 语言设置：0=跟随系统 1=中文 2=英文
    fun saveLanguage(context: Context, lang: Int) {
        getPrefs(context).edit().putInt(KEY_LANGUAGE, lang).apply()
        isChanged = true
    }
    fun getLanguage(context: Context) = getPrefs(context).getInt(KEY_LANGUAGE, 0)

    // 是否展示特惠界面（默认开启）
    fun saveShowSpecials(context: Context, enable: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_SPECIALS, enable).apply()
        isChanged = true
    }
    fun isSpecialsEnabled(context: Context) = getPrefs(context).getBoolean(KEY_SHOW_SPECIALS, true)

    /** 检测语言是否在上次应用后发生了变化（用于触发 Activity 重建） */
    fun hasLanguageChanged(context: Context): Boolean {
        val prefs = getPrefs(context)
        val current = getLanguage(context)
        val lastApplied = prefs.getInt("last_applied_language", -1)
        return current != lastApplied
    }

    /** 标记当前语言已应用（调用后 hasLanguageChanged 返回 false） */
    fun markLanguageApplied(context: Context) {
        getPrefs(context).edit().putInt("last_applied_language", getLanguage(context)).apply()
    }
}
