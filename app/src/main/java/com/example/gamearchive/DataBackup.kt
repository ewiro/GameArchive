package com.example.gamearchive

import android.content.Context
import org.json.JSONObject

/** 配置导出/导入 — 将标记、标签、个人资料 URL、偏好设置序列化为单个 JSON 文件 */
object DataBackup {

    private const val VERSION = 1

    /** 导出所有可迁移配置为 JSON 字符串。
     *  排除 api_key、steam_id 等凭证信息。 */
    fun exportToJson(context: Context): String {
        val root = JSONObject()
        root.put("version", VERSION)

        // ── 游戏标记 ──
        root.put("game_marks", preferencesToJson(context, GameMarks.PREF_NAME))

        // ── 标签库 ──
        root.put("game_tags_lib", preferencesToJson(context, GameTags.LIB_PREF))

        // ── 游戏标签映射 ──
        root.put("game_tags_map", preferencesToJson(context, GameTags.MAP_PREF))

        // ── 用户资料（仅导出外观 URL，排除凭证） ──
        val userData = JSONObject()
        val userPrefs = context.getSharedPreferences(UserPrefs.PREF_NAME, Context.MODE_PRIVATE)
        userData.put("custom_bg_url", userPrefs.getString("custom_bg_url", "") ?: "")
        userData.put("custom_frame_url", userPrefs.getString("custom_frame_url", "") ?: "")
        userData.put("custom_avatar_url", userPrefs.getString("custom_avatar_url", "") ?: "")
        userData.put("show_profile_card", userPrefs.getBoolean("show_profile_card", false))
        root.put("steam_user_data", userData)

        // ── 游戏备注 ──
        root.put("game_notes", preferencesToJson(context, GameNotes.PREF_NAME))

        // ── 自定义游戏名 ──
        root.put("game_names", preferencesToJson(context, GameNames.PREF_NAME))

        // ── 活动统计（不包含任何账号凭证） ──
        root.put("activity_stats", preferencesToJson(context, ActivityStats.PREF_NAME))

        // ── 偏好设置 ──
        val prefs = JSONObject()
        val themePrefs = context.getSharedPreferences(ThemeUtils.PREF_NAME, Context.MODE_PRIVATE)
        prefs.put("theme_mode", themePrefs.getInt("theme_mode", 2))
        prefs.put("language", themePrefs.getInt("language", 0))
        prefs.put("enable_grouping", themePrefs.getBoolean("enable_grouping", false))
        prefs.put("sort_mode", themePrefs.getInt("sort_mode", 0))
        prefs.put("show_specials", themePrefs.getBoolean("show_specials", true))
        prefs.put("show_bangumi", themePrefs.getBoolean("show_bangumi", true))
        prefs.put("bangumi_display_style", themePrefs.getInt("bangumi_display_style", 0))
        prefs.put("bangumi_rating_mode", UserPrefs.getBangumiRatingMode(context))
        root.put("app_theme_prefs", prefs)

        return root.toString(2)
    }

    /** 从 JSON 字符串导入配置。成功返回 true，解析失败返回 false。 */
    fun importFromJson(context: Context, json: String): Boolean {
        return try {
            val root = JSONObject(json)

            // ── 恢复游戏标记 ──
            if (root.has("game_marks")) {
                val editor = context.getSharedPreferences(GameMarks.PREF_NAME, Context.MODE_PRIVATE).edit().clear()
                val marks = root.getJSONObject("game_marks")
                marks.keys().forEach { key -> editor.putString(key, marks.getString(key)) }
                editor.apply()
            }

            // ── 恢复标签库 ──
            if (root.has("game_tags_lib")) {
                val editor = context.getSharedPreferences(GameTags.LIB_PREF, Context.MODE_PRIVATE).edit().clear()
                val lib = root.getJSONObject("game_tags_lib")
                lib.keys().forEach { key -> editor.putString(key, lib.getString(key)) }
                editor.apply()
            }

            // ── 恢复游戏标签映射 ──
            if (root.has("game_tags_map")) {
                val editor = context.getSharedPreferences(GameTags.MAP_PREF, Context.MODE_PRIVATE).edit().clear()
                val map = root.getJSONObject("game_tags_map")
                map.keys().forEach { key -> editor.putString(key, map.getString(key)) }
                editor.apply()
            }

            // ── 恢复用户资料（仅外观 URL） ──
            if (root.has("steam_user_data")) {
                val data = root.getJSONObject("steam_user_data")
                val editor = context.getSharedPreferences(UserPrefs.PREF_NAME, Context.MODE_PRIVATE).edit()
                if (data.has("custom_bg_url")) editor.putString("custom_bg_url", data.getString("custom_bg_url"))
                if (data.has("custom_frame_url")) editor.putString("custom_frame_url", data.getString("custom_frame_url"))
                if (data.has("custom_avatar_url")) editor.putString("custom_avatar_url", data.getString("custom_avatar_url"))
                if (data.has("show_profile_card")) editor.putBoolean("show_profile_card", data.getBoolean("show_profile_card"))
                editor.apply()
            }

            // ── 恢复游戏备注 ──
            if (root.has("game_notes")) {
                val editor = context.getSharedPreferences(GameNotes.PREF_NAME, Context.MODE_PRIVATE).edit().clear()
                val notes = root.getJSONObject("game_notes")
                notes.keys().forEach { key -> editor.putString(key, notes.getString(key)) }
                editor.apply()
            }

            // ── 恢复自定义游戏名 ──
            if (root.has("game_names")) {
                val editor = context.getSharedPreferences(GameNames.PREF_NAME, Context.MODE_PRIVATE).edit().clear()
                val names = root.getJSONObject("game_names")
                names.keys().forEach { key -> editor.putString(key, names.getString(key)) }
                editor.apply()
            }

            // ── 恢复活动统计 ──
            if (root.has("activity_stats")) {
                val editor = context.getSharedPreferences(ActivityStats.PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                val stats = root.getJSONObject("activity_stats")
                stats.keys().forEach { key -> editor.putString(key, stats.getString(key)) }
                editor.apply()
                ActivityStats.notifyChanged()
            }

            // ── 恢复偏好设置 ──
            if (root.has("app_theme_prefs")) {
                val p = root.getJSONObject("app_theme_prefs")
                val editor = context.getSharedPreferences(ThemeUtils.PREF_NAME, Context.MODE_PRIVATE).edit()
                if (p.has("theme_mode")) editor.putInt("theme_mode", p.getInt("theme_mode"))
                if (p.has("language")) editor.putInt("language", p.getInt("language"))
                if (p.has("enable_grouping")) editor.putBoolean("enable_grouping", p.getBoolean("enable_grouping"))
                if (p.has("sort_mode")) editor.putInt("sort_mode", p.getInt("sort_mode"))
                if (p.has("show_specials")) editor.putBoolean("show_specials", p.getBoolean("show_specials"))
                if (p.has("show_bangumi")) editor.putBoolean("show_bangumi", p.getBoolean("show_bangumi"))
                if (p.has("bangumi_display_style")) {
                    editor.putInt("bangumi_display_style", p.getInt("bangumi_display_style").coerceIn(0, 1))
                }
                // 兼容旧格式：跳过 group_recent 字段（已废弃）
                editor.apply()
                if (p.has("bangumi_rating_mode")) {
                    UserPrefs.setBangumiRatingMode(
                        context,
                        p.getInt("bangumi_rating_mode").coerceIn(0, 2)
                    )
                }
            }

            ThemeUtils.isChanged = true
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun preferencesToJson(context: Context, preferenceName: String): JSONObject =
        JSONObject().also { json ->
            context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).all
                .forEach { (key, value) -> json.put(key, value) }
        }
}
