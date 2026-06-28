package com.example.gamearchive

import android.content.Context

/** 游戏标记系统 — 8 种游玩状态，纯本地存储，用稳定字符串 Key 防资源 ID 漂移 */
object GameMarks {

    private const val PREF_NAME = "game_marks"

    // ── 稳定字符串 Key（不随编译变化） ──
    private const val KEY_UNPLAYED = "unplayed"
    private const val KEY_PLAYING = "playing"
    private const val KEY_COMPLETED = "completed"
    private const val KEY_MULTI = "multi"
    private const val KEY_LONGTERM = "longterm"
    private const val KEY_PERFECTED = "perfected"
    private const val KEY_SHELVED = "shelved"
    private const val KEY_ABANDONED = "abandoned"

    /** 字符串 Key → 资源 ID */
    private val keyToResId = mapOf(
        KEY_UNPLAYED   to R.string.mark_unplayed,
        KEY_PLAYING    to R.string.mark_playing,
        KEY_COMPLETED  to R.string.mark_completed,
        KEY_MULTI      to R.string.mark_multi_completed,
        KEY_LONGTERM   to R.string.mark_longterm,
        KEY_PERFECTED  to R.string.mark_perfected,
        KEY_SHELVED    to R.string.mark_shelved,
        KEY_ABANDONED  to R.string.mark_abandoned,
    )

    /** 资源 ID → 字符串 Key（用于写入） */
    private val resIdToKey = keyToResId.entries.associate { (k, v) -> v to k }

    /** 资源 ID → 颜色（深色高饱和版） */
    val statusColorMap = mapOf(
        R.string.mark_unplayed       to 0xFF757575.toInt(),  // gray
        R.string.mark_playing        to 0xFF1565C0.toInt(),  // blue
        R.string.mark_completed      to 0xFF2E7D32.toInt(),  // green
        R.string.mark_multi_completed to 0xFF7B1FA2.toInt(), // purple
        R.string.mark_longterm       to 0xFFE65100.toInt(),  // orange
        R.string.mark_perfected      to 0xFFFF8F00.toInt(),  // gold
        R.string.mark_shelved        to 0xFF4E342E.toInt(),  // brown
        R.string.mark_abandoned      to 0xFFC62828.toInt()   // red
    )

    /** 资源 ID 列表（顺序固定，用于 UI 排序） */
    val markResIds = listOf(
        R.string.mark_unplayed,
        R.string.mark_playing,
        R.string.mark_completed,
        R.string.mark_multi_completed,
        R.string.mark_longterm,
        R.string.mark_perfected,
        R.string.mark_shelved,
        R.string.mark_abandoned
    )

    /** 读取标记（返回资源 ID，无标记返回 -1） */
    fun getMark(context: Context, appId: Int): Int {
        val key = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString("mark_$appId", null) ?: return -1
        return keyToResId[key] ?: -1
    }

    /** 设置标记（传入资源 ID 或 -1 清除） */
    fun setMark(context: Context, appId: Int, markResId: Int) {
        val editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
        if (markResId == -1) {
            editor.remove("mark_$appId")
        } else {
            val key = resIdToKey[markResId] ?: return
            editor.putString("mark_$appId", key)
        }
        editor.commit()
    }
}
