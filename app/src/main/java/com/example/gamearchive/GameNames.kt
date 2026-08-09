package com.example.gamearchive

import androidx.core.content.edit
import android.content.Context

/** 游戏自定义命名 — 优先于 Steam 原始名称显示 */
object GameNames {

    const val PREF_NAME = "game_names"

    fun getName(context: Context, appId: Int): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString("rename_$appId", null)
    }

    /** 一次读取全部自定义名称，供长列表复用。 */
    fun getAllNames(context: Context): Map<Int, String> =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).all
            .mapNotNull { (key, value) ->
                val appId = key.removePrefix("rename_").takeIf { key.startsWith("rename_") }
                    ?.toIntOrNull() ?: return@mapNotNull null
                val name = (value as? String)?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                appId to name
            }
            .toMap()

    fun setName(context: Context, appId: Int, name: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {putString("rename_$appId", name.trim())}
    }

    fun deleteName(context: Context, appId: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {remove("rename_$appId")}
    }
}
