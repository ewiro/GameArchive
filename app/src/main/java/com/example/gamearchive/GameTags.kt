package com.example.gamearchive

import android.content.Context
import org.json.JSONArray

/** 标签系统 — 用户自定义标签，纯本地存储 */
object GameTags {

    const val LIB_PREF = "game_tags_lib"
    const val MAP_PREF = "game_tags_map"

    // ── 标签库操作 ──

    /** 获取全部标签列表 */
    fun getAllTags(context: Context): List<String> {
        val json = context.getSharedPreferences(LIB_PREF, Context.MODE_PRIVATE)
            .getString("all_tags", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    /** 新增标签（已存在则忽略） */
    fun addTag(context: Context, tag: String) {
        val tags = getAllTags(context).toMutableList()
        if (!tags.contains(tag)) {
            tags.add(tag)
            saveAllTags(context, tags)
        }
    }

    /** 删除标签（同时从所有游戏中移除） */
    fun deleteTag(context: Context, tag: String) {
        val tags = getAllTags(context).toMutableList()
        tags.remove(tag)
        saveAllTags(context, tags)

        // 从所有游戏中移除该标签
        val mapPrefs = context.getSharedPreferences(MAP_PREF, Context.MODE_PRIVATE)
        val allKeys = mapPrefs.all.keys.filter { it.startsWith("tags_") }
        val editor = mapPrefs.edit()
        allKeys.forEach { key ->
            val gameTags = getTagsForGameRaw(mapPrefs, key)
            if (gameTags.contains(tag)) {
                val updated = gameTags.filter { it != tag }
                if (updated.isEmpty()) editor.remove(key)
                else editor.putString(key, JSONArray(updated).toString())
            }
        }
        editor.apply()
    }

    /** 重命名标签 */
    fun renameTag(context: Context, oldName: String, newName: String) {
        val tags = getAllTags(context).toMutableList()
        val idx = tags.indexOf(oldName)
        if (idx >= 0) tags[idx] = newName
        saveAllTags(context, tags)

        // 更新所有游戏中的标签名
        val mapPrefs = context.getSharedPreferences(MAP_PREF, Context.MODE_PRIVATE)
        val allKeys = mapPrefs.all.keys.filter { it.startsWith("tags_") }
        val editor = mapPrefs.edit()
        allKeys.forEach { key ->
            val gameTags = getTagsForGameRaw(mapPrefs, key)
            if (gameTags.contains(oldName)) {
                val updated = gameTags.map { if (it == oldName) newName else it }
                editor.putString(key, JSONArray(updated).toString())
            }
        }
        editor.apply()
    }

    private fun saveAllTags(context: Context, tags: List<String>) {
        context.getSharedPreferences(LIB_PREF, Context.MODE_PRIVATE)
            .edit().putString("all_tags", JSONArray(tags).toString()).apply()
    }

    // ── 游戏-标签关联操作 ──

    /** 获取某个游戏的全部标签 */
    fun getTagsForGame(context: Context, appId: Int): List<String> {
        val prefs = context.getSharedPreferences(MAP_PREF, Context.MODE_PRIVATE)
        return getTagsForGameRaw(prefs, "tags_$appId")
    }

    /** 为游戏设置标签（全量替换） */
    fun setTagsForGame(context: Context, appId: Int, tags: List<String>) {
        val editor = context.getSharedPreferences(MAP_PREF, Context.MODE_PRIVATE).edit()
        if (tags.isEmpty()) {
            editor.remove("tags_$appId")
        } else {
            editor.putString("tags_$appId", JSONArray(tags).toString())
        }
        editor.apply()
    }

    /** 获取标签被使用的游戏数 */
    fun getTagUsageCount(context: Context, tag: String): Int {
        val mapPrefs = context.getSharedPreferences(MAP_PREF, Context.MODE_PRIVATE)
        val allKeys = mapPrefs.all.keys.filter { it.startsWith("tags_") }
        return allKeys.count { key -> getTagsForGameRaw(mapPrefs, key).contains(tag) }
    }

    private fun getTagsForGameRaw(prefs: android.content.SharedPreferences, key: String): List<String> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }
}
