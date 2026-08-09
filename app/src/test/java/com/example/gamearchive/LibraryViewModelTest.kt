package com.example.gamearchive

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryViewModelTest {
    @Test
    fun multiAccountGamesKeepFirstOccurrenceAndStableOrder() {
        val primary = listOf(game(1, "Primary One"), game(2, "Primary Two"))
        val secondary = listOf(game(2, "Duplicate Two"), game(3, "Secondary Three"))

        val merged = mergeOwnedGames(listOf(primary, secondary))

        assertEquals(listOf(1, 2, 3), merged.map(GameInfo::appid))
        assertEquals("Primary Two", merged[1].name)
    }

    @Test
    fun cancellableResultNeverSwallowsCancellation() {
        assertThrows(CancellationException::class.java) {
            runCatchingCancellable<Unit> { throw CancellationException("cancel") }
        }
    }

    @Test
    fun keepsInventoryWhenOptionalProfileRequestFails() = runBlocking {
        val owned = listOf(game(8, "Owned"))
        val source = object : LibraryDataSource {
            override suspend fun player(apiKey: String, steamId: String): PlayerInfo? =
                error("profile unavailable")

            override suspend fun level(apiKey: String, steamId: String) = 0
            override suspend fun games(apiKey: String, steamId: String) = owned
            override suspend fun prices(appIds: String, language: String) =
                emptyMap<String, StoreAppDetails>()
            override suspend fun reviews(appId: Int, language: String) =
                ReviewResponse(null, emptyList())
        }
        val viewModel = LibraryViewModel(source)

        viewModel.refresh("key", "steam")
        val state = withTimeout(5_000) { viewModel.uiState.first { it.games != null } }

        assertEquals(listOf(8), state.games.orEmpty().map(GameInfo::appid))
        assertNull(state.player)
    }

    private fun game(id: Int, name: String) = GameInfo(
        appid = id,
        name = name,
        playtime_forever = 0,
        img_icon_url = ""
    )
}
