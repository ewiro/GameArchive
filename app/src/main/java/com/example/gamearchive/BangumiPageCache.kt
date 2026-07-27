package com.example.gamearchive

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.Gson

@Keep
data class BangumiPageSnapshot(
    val username: String,
    val user: BangumiUser?,
    val collections: Map<Int, List<BangumiCollection>>,
    val ratings: Map<Int, Any?>,
    val episodeTotals: Map<Int, Int>,
    val watchedEpisodeCounts: Map<Int, Int>
)

object BangumiPageCache {
    private const val PREF_NAME = "bangumi_page_cache"
    private const val KEY_SNAPSHOT = "snapshot"
    private val gson = Gson()

    fun load(context: Context, username: String): BangumiPageSnapshot? {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SNAPSHOT, null)
            ?: return null
        return runCatching {
            gson.fromJson(json, BangumiPageSnapshot::class.java)
        }.getOrNull()?.takeIf { it.username == username }
    }

    fun save(context: Context, snapshot: BangumiPageSnapshot) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SNAPSHOT, gson.toJson(snapshot))
            .apply()
    }
}
