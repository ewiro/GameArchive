package com.example.gamearchive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataBackupTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf(
            GameMarks.PREF_NAME,
            GameTags.LIB_PREF,
            GameTags.MAP_PREF,
            GameNotes.PREF_NAME,
            GameNames.PREF_NAME,
            ActivityStats.PREF_NAME,
            BangumiTagOrder.PREF_NAME,
            UserPrefs.PREF_NAME,
            UserPrefs.CREDENTIAL_PREF_NAME,
            ThemeUtils.PREF_NAME
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun exportExcludesSteamAndBangumiCredentials() {
        UserPrefs.saveCredentials(context, "steam-secret", "76561198000000000")
        UserPrefs.saveBangumiAuthorization(context, "access-secret", "refresh-secret", 42)

        val exported = DataBackup.exportToJson(context)

        assertFalse(exported.contains("steam-secret"))
        assertFalse(exported.contains("76561198000000000"))
        assertFalse(exported.contains("access-secret"))
        assertFalse(exported.contains("refresh-secret"))
    }

    @Test
    fun invalidLaterSectionDoesNotOverwriteEarlierData() {
        GameMarks.setMark(context, 10, R.string.mark_playing)
        GameTags.addTag(context, "原标签")
        val invalid = JSONObject()
            .put("version", 1)
            .put("game_marks", JSONObject().put("mark_10", "completed"))
            .put("game_tags_lib", JSONObject().put("all_tags", 123))
            .toString()

        assertFalse(DataBackup.importFromJson(context, invalid))
        assertEquals(R.string.mark_playing, GameMarks.getMark(context, 10))
        assertEquals(listOf("原标签"), GameTags.getAllTags(context))
    }

    @Test
    fun validBackupRestoresLocalDataWithoutChangingCredentials() {
        GameMarks.setMark(context, 10, R.string.mark_completed)
        GameTags.addTag(context, "剧情")
        GameTags.setTagsForGame(context, 10, listOf("剧情"))
        GameNotes.setNote(context, 10, "原备注")
        UserPrefs.saveCustomBgUrl(context, "https://example.com/original.jpg")
        UserPrefs.saveCredentials(context, "steam-secret", "76561198000000000")
        val exported = DataBackup.exportToJson(context)

        GameMarks.setMark(context, 10, R.string.mark_abandoned)
        GameTags.addTag(context, "临时标签")
        GameNotes.setNote(context, 10, "临时备注")
        UserPrefs.saveCustomBgUrl(context, "https://example.com/temporary.jpg")

        assertTrue(DataBackup.importFromJson(context, exported))
        assertEquals(R.string.mark_completed, GameMarks.getMark(context, 10))
        assertEquals(listOf("剧情"), GameTags.getAllTags(context))
        assertEquals(listOf("剧情"), GameTags.getTagsForGame(context, 10))
        assertEquals("原备注", GameNotes.getNote(context, 10))
        assertEquals("https://example.com/original.jpg", UserPrefs.getCustomBgUrl(context))
        assertEquals("steam-secret", UserPrefs.getApiKey(context))
        assertEquals("76561198000000000", UserPrefs.getSteamId(context))
    }

    @Test
    fun unsupportedBackupVersionLeavesDataUntouched() {
        GameNotes.setNote(context, 10, "保留内容")
        val unsupported = JSONObject()
            .put("version", 2)
            .put("game_notes", JSONObject().put("note_10", "覆盖内容"))
            .toString()

        assertFalse(DataBackup.importFromJson(context, unsupported))
        assertEquals("保留内容", GameNotes.getNote(context, 10))
    }
}
