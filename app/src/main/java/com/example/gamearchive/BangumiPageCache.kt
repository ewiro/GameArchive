package com.example.gamearchive

import androidx.core.content.edit
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

    @Synchronized
    fun load(context: Context, username: String): BangumiPageSnapshot? {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SNAPSHOT, null)
            ?: return null
        return runCatching {
            gson.fromJson(json, BangumiPageSnapshot::class.java)
        }.getOrNull()?.takeIf { it.username == username }
    }

    @Synchronized
    fun save(context: Context, snapshot: BangumiPageSnapshot) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_SNAPSHOT, gson.toJson(snapshot))
            }
    }

    @Synchronized
    fun updateCollection(
        context: Context,
        username: String,
        collection: BangumiCollection,
        rating: Double?,
        episodeTotal: Int?,
        watchedEpisodeCount: Int?
    ): Boolean {
        val snapshot = load(context, username) ?: return false
        val existingCollection = snapshot.collections.values
            .flatten()
            .firstOrNull { it.subject_id == collection.subject_id }
        val updatedCollection = if (collection.subject == null) {
            collection.copy(subject = existingCollection?.subject)
        } else {
            collection
        }
        val updatedCollections = snapshot.collections
            .mapValuesTo(mutableMapOf()) { (_, items) ->
                items.filterNot { it.subject_id == collection.subject_id }.toMutableList()
            }
        updatedCollections.entries.removeAll { it.value.isEmpty() }
        updatedCollections.getOrPut(updatedCollection.type) { mutableListOf() }
            .add(0, updatedCollection)

        val updatedRatings = snapshot.ratings.toMutableMap().apply {
            if (rating != null) put(collection.subject_id, rating)
        }
        val updatedEpisodeTotals = snapshot.episodeTotals.toMutableMap().apply {
            if (episodeTotal != null) put(collection.subject_id, episodeTotal)
        }
        val updatedWatchedCounts = snapshot.watchedEpisodeCounts.toMutableMap().apply {
            if (watchedEpisodeCount != null) put(collection.subject_id, watchedEpisodeCount)
        }
        save(
            context,
            snapshot.copy(
                collections = updatedCollections,
                ratings = updatedRatings,
                episodeTotals = updatedEpisodeTotals,
                watchedEpisodeCounts = updatedWatchedCounts
            )
        )
        return true
    }
}
