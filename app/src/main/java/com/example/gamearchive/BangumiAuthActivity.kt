package com.example.gamearchive

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class BangumiAuthActivity : ComponentActivity() {

    companion object {
        private const val AUTH_URL = "https://bgm.tv/oauth/authorize"
        private const val TAG = "BangumiAuth"
    }

    private var tokenExchangeStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val clientId = AppConfig.BANGUMI_CLIENT_ID
        val clientSecret = AppConfig.BANGUMI_CLIENT_SECRET
        val redirectUri = AppConfig.BANGUMI_REDIRECT_URI
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            Toast.makeText(this, R.string.bangumi_oauth_config_missing, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val data = intent.data
        if (data != null) {
            handleCallback(data, clientId, clientSecret, redirectUri)
            return
        }

        val state = runCatching { BangumiOAuthState.issue(this) }.getOrElse { error ->
            Log.e(TAG, "Unable to persist OAuth state", error)
            Toast.makeText(this, R.string.bangumi_oauth_state_invalid, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val authUrl = AUTH_URL.toUri().buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("state", state)
            .build()
        startActivity(Intent(Intent.ACTION_VIEW, authUrl))
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { data ->
            handleCallback(
                data,
                AppConfig.BANGUMI_CLIENT_ID,
                AppConfig.BANGUMI_CLIENT_SECRET,
                AppConfig.BANGUMI_REDIRECT_URI
            )
        }
    }

    private fun handleCallback(
        data: Uri,
        clientId: String,
        clientSecret: String,
        redirectUri: String
    ) {
        if (!isExpectedCallback(data, redirectUri) ||
            !BangumiOAuthState.consume(this, data.getQueryParameter("state"))
        ) {
            Toast.makeText(this, R.string.bangumi_oauth_state_invalid, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val code = data.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            Toast.makeText(this, R.string.bangumi_oauth_canceled, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        exchangeCodeForToken(code, clientId, clientSecret, redirectUri)
    }

    private fun isExpectedCallback(data: Uri, redirectUri: String): Boolean {
        val expected = redirectUri.toUri()
        return data.scheme == expected.scheme &&
            data.authority == expected.authority &&
            data.path == expected.path
    }

    private fun exchangeCodeForToken(
        code: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String
    ) {
        if (tokenExchangeStarted) return
        tokenExchangeStarted = true
        lifecycleScope.launch {
            try {
                val token = GameArchiveApp.bgmOAuthService.getToken(
                    grantType = "authorization_code",
                    clientId = clientId,
                    clientSecret = clientSecret,
                    code = code,
                    redirectUri = redirectUri
                )
                check(
                    UserPrefs.saveBangumiAuthorization(
                        context = this@BangumiAuthActivity,
                        accessToken = token.access_token,
                        refreshToken = token.refresh_token.orEmpty(),
                        userId = token.user_id
                    )
                )
                try {
                    val currentUser = GameArchiveApp.createAuthenticatedBgmService(token.access_token)
                        .getCurrentUser()
                    UserPrefs.setBangumiUsername(this@BangumiAuthActivity, currentUser.username)
                    UserPrefs.setBangumiNickname(
                        this@BangumiAuthActivity,
                        currentUser.nickname.ifBlank { currentUser.username }
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(TAG, "Token saved but profile lookup failed", error)
                }
                ThemeUtils.isChanged = true
                Toast.makeText(
                    this@BangumiAuthActivity,
                    getString(R.string.bangumi_oauth_success, token.user_id),
                    Toast.LENGTH_LONG
                ).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Toast.makeText(
                    this@BangumiAuthActivity,
                    getString(R.string.bangumi_oauth_failed, error.message.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
            }
            finish()
        }
    }
}
