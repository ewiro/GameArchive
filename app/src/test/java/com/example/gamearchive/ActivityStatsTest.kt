package com.example.gamearchive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityStatsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(ActivityStats.PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun importedRecordsAreIdempotentAndRejectInvalidValues() {
        val valid = ActivityImportRecord(
            kind = ActivityKind.GAME,
            id = 10,
            title = "Test Game",
            secondaryTitle = "",
            imageUrl = "",
            date = "2026-08-05",
            gameMinutes = 84
        )
        val invalidDate = valid.copy(id = 11, date = "2026-02-30")
        val invalidAmount = valid.copy(id = 12, gameMinutes = 0)

        assertEquals(1, ActivityStats.importRecords(context, listOf(valid, invalidDate, invalidAmount)))
        assertEquals(0, ActivityStats.importRecords(context, listOf(valid)))
        assertEquals(listOf(ItemActivityRecord("2026-08-05", 84.0)), ActivityStats.getGameRecords(context, 10))
    }

    @Test
    fun episodeFormattingKeepsFractionalProgress() {
        assertEquals("2", formatEpisodeAmount(2.0))
        assertEquals("1.5", formatEpisodeAmount(1.5))
    }
}
