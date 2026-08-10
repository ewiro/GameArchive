package com.example.gamearchive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BangumiViewModelTest {
    @Test
    fun cachedColdLoadKeepsPageCachedAndSilentlySyncsActivity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val username = "cached-user"
        context.getSharedPreferences(ActivityStats.PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val subject = BangumiSubject(
            id = 7,
            name = "Attack on Titan Season 3",
            name_cn = "进击的巨人 第三季",
            type = 2,
            summary = null,
            eps = 12,
            total_episodes = 12,
            rating = null,
            images = null,
            date = null
        )
        val cachedItem = BangumiCollection(
            subject_id = 7,
            subject_type = 2,
            rate = 0,
            type = 2,
            comment = null,
            tags = emptyList(),
            ep_status = 3,
            vol_status = 0,
            updated_at = null,
            `private` = false,
            subject = subject
        )
        val watchedAt = System.currentTimeMillis() - 24 * 60 * 60 * 1_000L
        val episodes = (1..7).map { number ->
            BangumiUserEpisodeCollection(
                episode = BangumiEpisode(id = number, type = 0, ep = number.toDouble()),
                type = 2,
                updated_at = watchedAt / 1_000L
            )
        }
        ActivityStats.syncBangumi(
            context,
            listOf(cachedItem.copy(ep_status = 1)),
            mapOf(7 to episodes.take(1)),
            observedAt = watchedAt
        )
        BangumiPageCache.save(
            context,
            BangumiPageSnapshot(
                username = username,
                user = null,
                collections = mapOf(2 to listOf(cachedItem)),
                ratings = emptyMap(),
                episodeTotals = emptyMap(),
                watchedEpisodeCounts = emptyMap()
            )
        )
        val pageNetworkCalls = AtomicInteger()
        val activityNetworkCalls = AtomicInteger()
        val source = object : BangumiDataSource {
            override suspend fun user(username: String): BangumiUser {
                pageNetworkCalls.incrementAndGet()
                error("network should not be called")
            }

            override suspend fun collections(
                username: String,
                type: Int,
                offset: Int
            ): BangumiPagedCollection {
                pageNetworkCalls.incrementAndGet()
                error("network should not be called")
            }

            override suspend fun subject(subjectId: Int): BangumiSubjectDetail {
                pageNetworkCalls.incrementAndGet()
                error("network should not be called")
            }

            override suspend fun episodeCollections(
                context: Context,
                subjectId: Int
            ): BangumiPagedEpisodeCollection {
                activityNetworkCalls.incrementAndGet()
                return BangumiPagedEpisodeCollection(
                    total = episodes.size,
                    limit = 100,
                    offset = 0,
                    data = episodes
                )
            }

            override suspend fun publicEpisodeTotal(subjectId: Int): Int {
                pageNetworkCalls.incrementAndGet()
                error("network should not be called")
            }
        }
        val viewModel = BangumiViewModel(source)

        viewModel.loadIfNeeded(username, "token", context)
        viewModel.loadIfNeeded(username, "token", context)
        val state = withTimeout(5_000) {
            viewModel.uiState.first { it.collections != null && !it.isLoading }
        }
        val records = withTimeout(5_000) {
            var loaded = ActivityStats.getAnimeRecords(context, 7)
            while (loaded.isEmpty()) {
                delay(10)
                loaded = ActivityStats.getAnimeRecords(context, 7)
            }
            loaded
        }

        assertEquals(listOf(7), state.collections?.get(2).orEmpty().map { it.subject_id })
        assertEquals(0, pageNetworkCalls.get())
        assertEquals(1, activityNetworkCalls.get())
        assertEquals(6.0, records.single().amount, 0.0)
    }

    @Test
    fun keepsSuccessfulBucketWhenOtherCollectionRequestsFail() = runBlocking {
        val item = BangumiCollection(
            subject_id = 9,
            subject_type = 2,
            rate = 0,
            type = 3,
            comment = null,
            tags = emptyList(),
            ep_status = 1,
            vol_status = 0,
            updated_at = null,
            `private` = false,
            subject = BangumiSubject(
                id = 9,
                name = "Anime",
                name_cn = null,
                type = 2,
                summary = null,
                eps = 12,
                total_episodes = 12,
                rating = mapOf("score" to 8.0),
                images = null,
                date = null
            )
        )
        val source = object : BangumiDataSource {
            override suspend fun user(username: String): BangumiUser = error("user unavailable")

            override suspend fun collections(
                username: String,
                type: Int,
                offset: Int
            ): BangumiPagedCollection {
                if (type != 3) error("bucket unavailable")
                return BangumiPagedCollection(1, 50, 0, listOf(item))
            }

            override suspend fun subject(subjectId: Int) = BangumiSubjectDetail(id = subjectId)
            override suspend fun episodeCollections(
                context: Context,
                subjectId: Int
            ) = BangumiPagedEpisodeCollection(0, 100, 0, emptyList())

            override suspend fun publicEpisodeTotal(subjectId: Int) = 0
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = BangumiViewModel(source)

        viewModel.refresh("tester", "", context)
        val state = withTimeout(5_000) {
            viewModel.uiState.first { !it.isRefreshing && it.collections != null }
        }

        assertEquals(listOf(9), state.collections?.get(2).orEmpty().map { it.subject_id })
        assertEquals(8.0, state.ratings[9] ?: 0.0, 0.0)
        assertNull(state.user)
    }

    @Test
    fun appliesSavedCollectionFromCacheWithoutRefreshingNetwork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val username = "local-update-user"
        val networkCalls = AtomicInteger()
        val original = collection(subjectId = 12, uiType = 2, episodeProgress = 3)
        BangumiPageCache.save(
            context,
            BangumiPageSnapshot(
                username = username,
                user = null,
                collections = mapOf(2 to listOf(original)),
                ratings = mapOf(12 to 7.0),
                episodeTotals = mapOf(12 to 12),
                watchedEpisodeCounts = mapOf(12 to 3)
            )
        )
        val source = object : BangumiDataSource {
            override suspend fun user(username: String): BangumiUser {
                networkCalls.incrementAndGet()
                error("network should not be called")
            }

            override suspend fun collections(
                username: String,
                type: Int,
                offset: Int
            ): BangumiPagedCollection {
                networkCalls.incrementAndGet()
                error("network should not be called")
            }

            override suspend fun subject(subjectId: Int): BangumiSubjectDetail {
                networkCalls.incrementAndGet()
                error("network should not be called")
            }

            override suspend fun episodeCollections(
                context: Context,
                subjectId: Int
            ): BangumiPagedEpisodeCollection {
                networkCalls.incrementAndGet()
                error("network should not be called")
            }

            override suspend fun publicEpisodeTotal(subjectId: Int): Int {
                networkCalls.incrementAndGet()
                error("network should not be called")
            }
        }
        val viewModel = BangumiViewModel(source)
        viewModel.loadIfNeeded(username, "", context)
        withTimeout(5_000) {
            viewModel.uiState.first { it.collections?.get(2)?.singleOrNull() != null }
        }

        val updated = original.copy(type = 3, ep_status = 12, rate = 9)
        BangumiPageCache.updateCollection(
            context = context,
            username = username,
            collection = updated,
            rating = 8.5,
            episodeTotal = 12,
            watchedEpisodeCount = 12
        )
        viewModel.applyCachedCollectionChange(username, "", context)
        val state = withTimeout(5_000) {
            viewModel.uiState.first {
                it.collections?.get(3)?.singleOrNull()?.subject_id == 12
            }
        }

        assertEquals(0, networkCalls.get())
        assertEquals(emptyList<BangumiCollection>(), state.collections?.get(2).orEmpty())
        assertEquals(9, state.collections?.get(3)?.single()?.rate)
        assertEquals(8.5, state.ratings[12] ?: 0.0, 0.0)
        assertEquals(12, state.watchedEpisodeCounts[12])
    }

    private fun collection(
        subjectId: Int,
        uiType: Int,
        episodeProgress: Int
    ) = BangumiCollection(
        subject_id = subjectId,
        subject_type = 2,
        rate = 0,
        type = uiType,
        comment = null,
        tags = emptyList(),
        ep_status = episodeProgress,
        vol_status = 0,
        updated_at = null,
        `private` = false,
        subject = BangumiSubject(
            id = subjectId,
            name = "Anime $subjectId",
            name_cn = null,
            type = 2,
            summary = null,
            eps = 12,
            total_episodes = 12,
            rating = null,
            images = null,
            date = null
        )
    )
}
