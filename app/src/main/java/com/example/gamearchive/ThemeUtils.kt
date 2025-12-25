package com.example.gamearchive

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

object ThemeUtils {
    // SharedPreferences 文件名
    private const val PREF_NAME = "app_theme_prefs"

    // 设置项键名定义
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_CUSTOM_COLOR = "custom_color"
    private const val KEY_PURE_BLACK = "pure_black"
    private const val KEY_ENABLE_GROUPING = "enable_grouping"
    private const val KEY_SORT_MODE = "sort_mode"
    private const val KEY_GROUP_RECENT = "group_recent"

    // 标记设置是否发生变化，用于通知 Activity 重启
    var isChanged = false

    // 预定义调色板颜色列表
    val COLOR_PALETTE = listOf(
        0xFFB71C1C.toInt(), 0xFFE65100.toInt(), 0xFFF9A825.toInt(),
        0xFF2E7D32.toInt(), 0xFF00695C.toInt(), 0xFF1565C0.toInt(),
        0xFF6A1B9A.toInt(), 0xFFC62828.toInt(), 0xFF455A64.toInt()
    )

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

        // 2. 设置动态颜色 (Material You)
        val useDynamic = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        if (useDynamic) {
            // 使用系统壁纸取色
            DynamicColors.applyToActivityIfAvailable(activity)
        } else {
            // 使用用户自定义颜色
            val color = prefs.getInt(KEY_CUSTOM_COLOR, COLOR_PALETTE[5])
            val options = DynamicColorsOptions.Builder().setContentBasedSource(color).build()
            DynamicColors.applyToActivityIfAvailable(activity, options)
        }

        // 3. 设置纯黑模式 (仅在深色模式下生效)
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

    // 保存动态颜色开关
    fun saveDynamicColor(context: Context, enable: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DYNAMIC_COLOR, enable).apply()
        isChanged = true
    }
    fun isDynamicColorEnabled(context: Context) = getPrefs(context).getBoolean(KEY_DYNAMIC_COLOR, true)

    // 保存自定义颜色
    fun saveCustomColor(context: Context, color: Int) {
        getPrefs(context).edit().putInt(KEY_CUSTOM_COLOR, color).apply()
        isChanged = true
    }

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
}