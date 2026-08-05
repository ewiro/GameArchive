package com.example.gamearchive

import android.content.Context
import android.content.SharedPreferences
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

        // ── Bangumi 标签输入顺序 ──
        root.put("bangumi_tag_order", preferencesToJson(context, BangumiTagOrder.PREF_NAME))

        // ── 偏好设置 ──
        val prefs = JSONObject()
        val themePrefs = context.getSharedPreferences(ThemeUtils.PREF_NAME, Context.MODE_PRIVATE)
        prefs.put("theme_mode", themePrefs.getInt("theme_mode", 2))
        prefs.put("language", themePrefs.getInt("language", 0))
        prefs.put(
            "show_playtime_badge_background",
            themePrefs.getBoolean("show_playtime_badge_background", true)
        )
        prefs.put(
            "use_playtime_badge_text_color",
            themePrefs.getBoolean("use_playtime_badge_text_color", false)
        )
        prefs.put("enable_grouping", themePrefs.getBoolean("enable_grouping", false))
        prefs.put("sort_mode", themePrefs.getInt("sort_mode", 0))
        prefs.put("show_specials", themePrefs.getBoolean("show_specials", true))
        prefs.put("show_bangumi", themePrefs.getBoolean("show_bangumi", true))
        prefs.put("bangumi_display_style", themePrefs.getInt("bangumi_display_style", 0))
        prefs.put("bangumi_rating_mode", UserPrefs.getBangumiRatingMode(context))
        root.put("app_theme_prefs", prefs)

        return root.toString(2)
    }

    /** 从 JSON 字符串导入配置。导入前完整校验，任一写入失败时恢复原数据。 */
    @Synchronized
    fun importFromJson(context: Context, json: String): Boolean {
        val backup = runCatching { parseBackup(json) }.getOrElse { return false }
        val touchedPrefs = buildSet {
            backup.sections.forEach { add(it.preferenceName) }
            if (backup.userPrefs.isNotEmpty()) add(UserPrefs.PREF_NAME)
            if (backup.themePrefs.isNotEmpty()) add(ThemeUtils.PREF_NAME)
        }
        val originals = touchedPrefs.associateWith { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap()
        }

        return try {
            backup.sections.forEach { section ->
                check(writePreferences(context, section.preferenceName, section.values, true))
            }
            if (backup.userPrefs.isNotEmpty()) {
                check(writePreferences(context, UserPrefs.PREF_NAME, backup.userPrefs, false))
            }
            if (backup.themePrefs.isNotEmpty()) {
                check(writePreferences(context, ThemeUtils.PREF_NAME, backup.themePrefs, false))
            }
            if (backup.hasActivityStats) ActivityStats.notifyChanged()
            ThemeUtils.isChanged = true
            true
        } catch (_: Exception) {
            originals.forEach { (name, values) ->
                writePreferences(context, name, values, true)
            }
            false
        }
    }

    private fun parseBackup(json: String): ParsedBackup {
        val root = JSONObject(json)
        require(root.optInt("version", -1) == VERSION)

        val sections = STRING_SECTIONS.mapNotNull { (jsonKey, preferenceName) ->
            if (!root.has(jsonKey)) return@mapNotNull null
            PreferenceSection(preferenceName, readStringObject(root.getJSONObject(jsonKey)))
        }
        val userPrefs = mutableMapOf<String, Any>()
        if (root.has("steam_user_data")) {
            val data = root.getJSONObject("steam_user_data")
            USER_STRING_KEYS.forEach { key ->
                if (data.has(key)) userPrefs[key] = requireString(data, key)
            }
            if (data.has("show_profile_card")) {
                userPrefs["show_profile_card"] = requireBoolean(data, "show_profile_card")
            }
        }

        val themePrefs = mutableMapOf<String, Any>()
        if (root.has("app_theme_prefs")) {
            val data = root.getJSONObject("app_theme_prefs")
            THEME_INT_KEYS.forEach { key ->
                if (data.has(key)) themePrefs[key] = requireInt(data, key)
            }
            THEME_BOOLEAN_KEYS.forEach { key ->
                if (data.has(key)) themePrefs[key] = requireBoolean(data, key)
            }
            if (data.has("bangumi_display_style")) {
                themePrefs["bangumi_display_style"] =
                    requireInt(data, "bangumi_display_style").coerceIn(0, 1)
            }
            if (data.has("bangumi_rating_mode")) {
                userPrefs["bangumi_rating_mode"] =
                    requireInt(data, "bangumi_rating_mode").coerceIn(0, 2)
            }
        }

        require(
            sections.isNotEmpty() ||
                root.has("steam_user_data") ||
                root.has("app_theme_prefs")
        )
        return ParsedBackup(
            sections = sections,
            userPrefs = userPrefs,
            themePrefs = themePrefs,
            hasActivityStats = root.has("activity_stats")
        )
    }

    private fun readStringObject(source: JSONObject): Map<String, Any> = buildMap {
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, requireString(source, key))
        }
    }

    private fun requireString(source: JSONObject, key: String): String =
        (source.get(key) as? String) ?: throw IllegalArgumentException(key)

    private fun requireBoolean(source: JSONObject, key: String): Boolean =
        (source.get(key) as? Boolean) ?: throw IllegalArgumentException(key)

    private fun requireInt(source: JSONObject, key: String): Int {
        val number = source.get(key) as? Number ?: throw IllegalArgumentException(key)
        val value = number.toLong()
        require(value in Int.MIN_VALUE..Int.MAX_VALUE && number.toDouble() == value.toDouble())
        return value.toInt()
    }

    private fun writePreferences(
        context: Context,
        preferenceName: String,
        values: Map<String, *>,
        clearFirst: Boolean
    ): Boolean {
        val editor = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit()
        if (clearFirst) editor.clear()
        values.forEach { (key, value) -> editor.putValue(key, value) }
        return editor.commit()
    }

    private fun SharedPreferences.Editor.putValue(
        key: String,
        value: Any?
    ): SharedPreferences.Editor = when (value) {
        is String -> putString(key, value)
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        is Boolean -> putBoolean(key, value)
        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        else -> throw IllegalArgumentException(key)
    }

    private fun preferencesToJson(context: Context, preferenceName: String): JSONObject =
        JSONObject().also { json ->
            context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).all
                .forEach { (key, value) -> json.put(key, value) }
        }

    private data class ParsedBackup(
        val sections: List<PreferenceSection>,
        val userPrefs: Map<String, Any>,
        val themePrefs: Map<String, Any>,
        val hasActivityStats: Boolean
    )

    private data class PreferenceSection(
        val preferenceName: String,
        val values: Map<String, Any>
    )

    private val STRING_SECTIONS = linkedMapOf(
        "game_marks" to GameMarks.PREF_NAME,
        "game_tags_lib" to GameTags.LIB_PREF,
        "game_tags_map" to GameTags.MAP_PREF,
        "game_notes" to GameNotes.PREF_NAME,
        "game_names" to GameNames.PREF_NAME,
        "activity_stats" to ActivityStats.PREF_NAME,
        "bangumi_tag_order" to BangumiTagOrder.PREF_NAME
    )
    private val USER_STRING_KEYS = listOf(
        "custom_bg_url",
        "custom_frame_url",
        "custom_avatar_url"
    )
    private val THEME_INT_KEYS = listOf("theme_mode", "language", "sort_mode")
    private val THEME_BOOLEAN_KEYS = listOf(
        "show_playtime_badge_background",
        "use_playtime_badge_text_color",
        "enable_grouping",
        "show_specials",
        "show_bangumi"
    )
}
