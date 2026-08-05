package com.example.gamearchive

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

object BangumiAuthSession {
    private val refreshMutex = Mutex()

    suspend fun <T> execute(
        context: Context,
        request: suspend (BangumiCollectionService) -> T
    ): T {
        val accessToken = UserPrefs.getBangumiAccessToken(context)
        check(accessToken.isNotBlank())
        return executeWithRefresh(
            initialToken = accessToken,
            serviceForToken = GameArchiveApp::createAuthenticatedBgmService,
            refreshToken = { rejectedToken, error ->
                refreshAccessToken(context, rejectedToken, error)
            },
            request = request
        )
    }

    internal suspend fun <S, T> executeWithRefresh(
        initialToken: String,
        serviceForToken: (String) -> S,
        refreshToken: suspend (String, HttpException) -> String,
        request: suspend (S) -> T
    ): T {
        return try {
            request(serviceForToken(initialToken))
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            val refreshedToken = refreshToken(initialToken, error)
            request(serviceForToken(refreshedToken))
        }
    }

    private suspend fun refreshAccessToken(
        context: Context,
        rejectedToken: String,
        authorizationError: HttpException
    ): String = refreshMutex.withLock {
        val currentToken = UserPrefs.getBangumiAccessToken(context)
        if (currentToken.isNotBlank() && currentToken != rejectedToken) return@withLock currentToken

        val refreshToken = UserPrefs.getBangumiRefreshToken(context)
        if (
            refreshToken.isBlank() ||
            AppConfig.BANGUMI_CLIENT_ID.isBlank() ||
            AppConfig.BANGUMI_CLIENT_SECRET.isBlank()
        ) {
            throw authorizationError
        }
        val token = try {
            GameArchiveApp.bgmOAuthService.getToken(
                grantType = "refresh_token",
                clientId = AppConfig.BANGUMI_CLIENT_ID,
                clientSecret = AppConfig.BANGUMI_CLIENT_SECRET,
                refreshToken = refreshToken,
                redirectUri = AppConfig.BANGUMI_REDIRECT_URI
            )
        } catch (_: Exception) {
            throw authorizationError
        }
        if (token.access_token.isBlank()) throw authorizationError
        val saved = UserPrefs.saveBangumiAuthorization(
            context = context,
            accessToken = token.access_token,
            refreshToken = token.refresh_token.orEmpty().ifBlank { refreshToken },
            userId = token.user_id
        )
        if (!saved) throw authorizationError
        GameArchiveApp.clearAuthenticatedBgmService()
        token.access_token
    }
}
