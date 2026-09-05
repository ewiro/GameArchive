package com.example.gamearchive

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class BangumiCollectionRecord(
    val subjectId: Int,
    val title: String,
    val secondaryTitle: String,
    val imageUrl: String,
    val previousApiType: Int,
    val currentApiType: Int,
    val previousEpisodes: Int,
    val currentEpisodes: Int,
    val currentEpisodeIds: List<Int>
)

internal interface BangumiCollectionDataSource {
    val authorized: Boolean
    var username: String
    val clientId: String
    suspend fun <T> authorizedRequest(request: suspend (BangumiCollectionService) -> T): T
    suspend fun preferredTags(subjectId: Int): List<String>?
    suspend fun saveTags(subjectId: Int, tags: List<String>)
    suspend fun cachedTagGroups(): List<List<String>>
    suspend fun recordSave(record: BangumiCollectionRecord)
    suspend fun updateCache(
        collection: BangumiCollection,
        rating: Double?,
        episodeTotal: Int?,
        watchedCount: Int
    )
    fun notifyCollectionChanged()
}

internal class DefaultBangumiCollectionDataSource(context: Context) : BangumiCollectionDataSource {
    private val context = context.applicationContext
    override val authorized get() = UserPrefs.getBangumiAccessToken(context).isNotEmpty()
    override val clientId get() = AppConfig.BANGUMI_CLIENT_ID
    override var username: String
        get() = UserPrefs.getBangumiUsername(context)
        set(value) = UserPrefs.setBangumiUsername(context, value)

    override suspend fun <T> authorizedRequest(request: suspend (BangumiCollectionService) -> T): T =
        BangumiAuthSession.execute(context, request)

    override suspend fun preferredTags(subjectId: Int): List<String>? = withContext(Dispatchers.IO) {
        BangumiTagOrder.get(context, username, subjectId)
    }

    override suspend fun saveTags(subjectId: Int, tags: List<String>) = withContext(Dispatchers.IO) {
        BangumiTagOrder.save(context, username, subjectId, tags)
    }

    override suspend fun cachedTagGroups(): List<List<String>> = withContext(Dispatchers.IO) {
        if (username.isBlank()) return@withContext emptyList()
        val collectionTags = BangumiPageCache.load(context, username)?.collections.orEmpty()
            .values.flatten().map { it.tags.orEmpty() }
        collectionTags + BangumiTagOrder.snapshot(context, username).values.toList()
    }

    override suspend fun recordSave(record: BangumiCollectionRecord) = withContext(Dispatchers.IO) {
        ActivityStats.recordBangumiSave(
            context = context,
            subjectId = record.subjectId,
            title = record.title,
            secondaryTitle = record.secondaryTitle,
            imageUrl = record.imageUrl,
            previousApiType = record.previousApiType,
            currentApiType = record.currentApiType,
            previousEpisodes = record.previousEpisodes,
            currentEpisodes = record.currentEpisodes,
            currentEpisodeIds = record.currentEpisodeIds
        )
    }

    override suspend fun updateCache(
        collection: BangumiCollection,
        rating: Double?,
        episodeTotal: Int?,
        watchedCount: Int
    ) {
        withContext(Dispatchers.IO) {
            BangumiPageCache.updateCollection(context, username, collection, rating, episodeTotal, watchedCount)
        }
    }

    override fun notifyCollectionChanged() {
        BangumiViewModel.collectionChanged = true
    }
}
