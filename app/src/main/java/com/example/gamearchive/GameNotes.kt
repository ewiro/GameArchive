package com.example.gamearchive

import androidx.core.content.edit
import android.content.Context

/** 本地游戏备注 — 每个游戏一段纯文本备注，纯本地存储 */
object GameNotes {

    const val PREF_NAME = "game_notes"

    /** 获取某游戏的备注，无则返回空字符串 */
    fun getNote(context: Context, appId: Int): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString("note_$appId", "") ?: ""
    }

    /** 保存某游戏的备注，传空字符串视为删除 */
    fun setNote(context: Context, appId: Int, note: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                putString("note_$appId", note)
            }
    }
}
