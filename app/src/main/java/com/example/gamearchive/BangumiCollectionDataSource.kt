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

internal suspend fun loadBangumiCollectionTypes(context: Context): Map<Int, Int> {
    var username = UserPrefs.getBangumiUsername(context)
    val cachedSnapshot = withContext(Dispatchers.IO) {
        if (username.isBlank()) null else BangumiPageCache.load(context, username)
    }
    if (cachedSnapshot != null) {
        return cachedSnapshot.collections
            .values
            .flatten()
            .associate { it.subject_id to it.type }
    }
    val token = UserPrefs.getBangumiAccessToken(context)
    return runCatchingCancellable {
        if (token.isNotEmpty()) {
            BangumiAuthSession.execute(context) { service ->
                if (username.isBlank()) {
                    username = service.getCurrentUser().username
                    UserPrefs.setBangumiUsername(context, username)
                }
                fetchBangumiCollectionTypes { offset ->
                    service.getUserCollections(
                        username = username,
                        subjectType = 2,
                        collectionType = null,
                        limit = 50,
                        offset = offset
                    )
                }
            }
        } else if (username.isNotBlank()) {
            fetchBangumiCollectionTypes { offset ->
                GameArchiveApp.bgmService.getUserCollections(
                    username = username,
                    subjectType = 2,
                    collectionType = null,
                    limit = 50,
                    offset = offset
                )
            }
        } else {
            emptyMap()
        }
    }.getOrDefault(emptyMap())
}

private suspend fun fetchBangumiCollectionTypes(
    loadPage: suspend (offset: Int) -> BangumiPagedCollection
): Map<Int, Int> {
    val result = linkedMapOf<Int, Int>()
    var offset = 0
    while (true) {
        val page = loadPage(offset)
        val collections = page.data.orEmpty()
        collections.forEach { collection ->
            result[collection.subject_id] = bangumiCollectionTypeToUi(collection.type)
        }
        if (collections.isEmpty() || offset + collections.size >= page.total) break
        offset += collections.size
    }
    return result
}
