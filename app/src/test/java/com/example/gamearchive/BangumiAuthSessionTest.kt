package com.example.gamearchive

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class BangumiAuthSessionTest {
    @Test
    fun unauthorizedRequestRefreshesAndRetriesOnce() = runBlocking {
        val requestedTokens = mutableListOf<String>()
        var refreshCount = 0

        val result = BangumiAuthSession.executeWithRefresh(
            initialToken = "expired",
            serviceForToken = { it },
            refreshToken = { rejectedToken, error ->
                assertEquals("expired", rejectedToken)
                assertEquals(401, error.code())
                refreshCount++
                "renewed"
            },
            request = { token ->
                requestedTokens += token
                if (token == "expired") throw httpError(401)
                "success"
            }
        )

        assertEquals("success", result)
        assertEquals(1, refreshCount)
        assertEquals(listOf("expired", "renewed"), requestedTokens)
    }

    @Test
    fun nonAuthorizationErrorDoesNotRefresh() = runBlocking {
        var refreshed = false
        try {
            BangumiAuthSession.executeWithRefresh(
                initialToken = "current",
                serviceForToken = { it },
                refreshToken = { _, _ -> refreshed = true; "renewed" },
                request = { throw httpError(500) }
            )
            fail("Expected HTTP 500")
        } catch (error: HttpException) {
            assertEquals(500, error.code())
        }
        assertFalse(refreshed)
    }

    @Test
    fun retriedAuthorizationErrorIsNotRetriedAgain() = runBlocking {
        var requestCount = 0
        var refreshCount = 0
        try {
            BangumiAuthSession.executeWithRefresh(
                initialToken = "expired",
                serviceForToken = { it },
                refreshToken = { _, _ -> refreshCount++; "renewed" },
                request = {
                    requestCount++
                    throw httpError(401)
                }
            )
            fail("Expected HTTP 401")
        } catch (error: HttpException) {
            assertEquals(401, error.code())
        }
        assertEquals(1, refreshCount)
        assertEquals(2, requestCount)
    }

    private fun httpError(code: Int): HttpException = HttpException(
        Response.error<Any>(
            code,
            "{}".toResponseBody("application/json".toMediaType())
        )
    )
}
