package com.example.gamearchive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class UserPrefsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(UserPrefs.PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(UserPrefs.CREDENTIAL_PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun migrateCredentialsMovesAccountsAndLeavesAppearanceSettings() {
        val legacy = context.getSharedPreferences(UserPrefs.PREF_NAME, Context.MODE_PRIVATE)
        legacy.edit()
            .putString("api_key", "steam-secret")
            .putString("steam_id", "76561198000000000")
            .putString("additional_accounts", "[]")
            .putString("bangumi_access_token", "access-secret")
            .putString("bangumi_refresh_token", "refresh-secret")
            .putInt("bangumi_user_id", 42)
            .putString("custom_bg_url", "https://example.com/background.jpg")
            .commit()

        UserPrefs.migrateCredentials(context)

        assertEquals("steam-secret", UserPrefs.getApiKey(context))
        assertEquals("76561198000000000", UserPrefs.getSteamId(context))
        assertEquals("access-secret", UserPrefs.getBangumiAccessToken(context))
        assertEquals("refresh-secret", UserPrefs.getBangumiRefreshToken(context))
        assertEquals(42, UserPrefs.getBangumiUserId(context))
        assertEquals("https://example.com/background.jpg", UserPrefs.getCustomBgUrl(context))
        assertFalse(legacy.contains("api_key"))
        assertFalse(legacy.contains("steam_id"))
        assertFalse(legacy.contains("bangumi_access_token"))
        assertTrue(legacy.contains("custom_bg_url"))
    }

    @Test
    fun newlySavedCredentialsOnlyUseExcludedPreferenceFile() {
        UserPrefs.saveCredentials(context, "steam-secret", "76561198000000000")
        assertTrue(
            UserPrefs.saveBangumiAuthorization(
                context,
                "access-secret",
                "refresh-secret",
                42
            )
        )

        val legacy = context.getSharedPreferences(UserPrefs.PREF_NAME, Context.MODE_PRIVATE)
        val credentials = context.getSharedPreferences(
            UserPrefs.CREDENTIAL_PREF_NAME,
            Context.MODE_PRIVATE
        )
        assertFalse(legacy.contains("api_key"))
        assertFalse(legacy.contains("bangumi_access_token"))
        assertEquals("steam-secret", credentials.getString("api_key", null))
        assertEquals("access-secret", credentials.getString("bangumi_access_token", null))
    }

    @Test
    fun oauthStateIsSingleUse() {
        val state = BangumiOAuthState.issue(context, nowMillis = 1_000L)

        assertTrue(BangumiOAuthState.consume(context, state, nowMillis = 2_000L))
        assertFalse(BangumiOAuthState.consume(context, state, nowMillis = 2_001L))
    }

    @Test
    fun invalidOauthStateClearsPendingRequest() {
        val state = BangumiOAuthState.issue(context, nowMillis = 1_000L)

        assertFalse(BangumiOAuthState.consume(context, "wrong-state", nowMillis = 2_000L))
        assertFalse(BangumiOAuthState.consume(context, state, nowMillis = 2_001L))
    }

    @Test
    fun expiredOauthStateIsRejected() {
        val state = BangumiOAuthState.issue(context, nowMillis = 1_000L)

        assertFalse(BangumiOAuthState.consume(context, state, nowMillis = 601_001L))
    }
}
