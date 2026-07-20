package com.example.gamearchive

import android.content.Context

/** 游戏自定义命名 — 优先于 Steam 原始名称显示 */
object GameNames {

    const val PREF_NAME = "game_names"

    fun getName(context: Context, appId: Int): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString("rename_$appId", null)
    }

    fun setName(context: Context, appId: Int, name: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString("rename_$appId", name.trim()).apply()
    }

    fun deleteName(context: Context, appId: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().remove("rename_$appId").apply()
    }
}
