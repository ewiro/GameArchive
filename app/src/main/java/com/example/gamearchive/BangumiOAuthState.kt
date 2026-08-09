package com.example.gamearchive

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

internal object BangumiOAuthState {
    private const val KEY_STATE = "bangumi_oauth_state"
    private const val KEY_CREATED_AT = "bangumi_oauth_state_created_at"
    private const val VALIDITY_MILLIS = 10 * 60 * 1000L
    private const val STATE_BYTES = 32

    private val secureRandom = SecureRandom()

    @Synchronized
    fun issue(context: Context, nowMillis: Long = System.currentTimeMillis()): String {
        val bytes = ByteArray(STATE_BYTES).also(secureRandom::nextBytes)
        val state = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        check(
            preferences(context).edit()
                .putString(KEY_STATE, state)
                .putLong(KEY_CREATED_AT, nowMillis)
                .commit()
        ) { "Unable to persist OAuth state" }
        return state
    }

    /** Consumes and clears the state before validation so every callback is strictly single-use. */
    @Synchronized
    fun consume(
        context: Context,
        receivedState: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val prefs = preferences(context)
        val expectedState = prefs.getString(KEY_STATE, null)
        val createdAt = prefs.getLong(KEY_CREATED_AT, 0L)
        if (!prefs.edit().remove(KEY_STATE).remove(KEY_CREATED_AT).commit()) return false
        if (receivedState == null || expectedState == null || createdAt <= 0L) return false
        val age = nowMillis - createdAt
        if (age !in 0..VALIDITY_MILLIS) return false
        return MessageDigest.isEqual(
            expectedState.toByteArray(Charsets.UTF_8),
            receivedState.toByteArray(Charsets.UTF_8)
        )
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        UserPrefs.CREDENTIAL_PREF_NAME,
        Context.MODE_PRIVATE
    )
}
