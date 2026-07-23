package com.example.gamearchive

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class BangumiAuthActivity : ComponentActivity() {

    companion object {
        private const val AUTH_URL = "https://bgm.tv/oauth/authorize"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val clientId = AppConfig.BANGUMI_CLIENT_ID
        val clientSecret = AppConfig.BANGUMI_CLIENT_SECRET
        val redirectUri = AppConfig.BANGUMI_REDIRECT_URI

        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            Toast.makeText(this, "请先在 AppConfig.kt 中填入 Bangumi OAuth 凭证", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 处理 OAuth 回调
        val data = intent.data
        if (data != null && data.scheme == "gamearchive" && data.host == "oauth") {
            val code = data.getQueryParameter("code")
            if (code != null) {
                exchangeCodeForToken(code, clientId, clientSecret, redirectUri)
            } else {
                Toast.makeText(this, "授权已取消", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            // 跳转到浏览器授权
            val authUrl = "$AUTH_URL?client_id=$clientId&response_type=code&redirect_uri=${Uri.encode(redirectUri)}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
            finish() // 此 Activity 已完成任务，等回调时重新创建
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { data ->
            if (data.scheme == "gamearchive" && data.host == "oauth") {
                val code = data.getQueryParameter("code")
                if (code != null) {
                    exchangeCodeForToken(code, AppConfig.BANGUMI_CLIENT_ID, AppConfig.BANGUMI_CLIENT_SECRET, AppConfig.BANGUMI_REDIRECT_URI)
                } else {
                    Toast.makeText(this, "授权已取消", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun exchangeCodeForToken(
        code: String, clientId: String, clientSecret: String, redirectUri: String
    ) {
        lifecycleScope.launch {
            try {
                val token = GameArchiveApp.bgmOAuthService.getToken(
                    grantType = "authorization_code",
                    clientId = clientId,
                    clientSecret = clientSecret,
                    code = code,
                    redirectUri = redirectUri
                )
                UserPrefs.setBangumiAccessToken(this@BangumiAuthActivity, token.access_token)
                UserPrefs.setBangumiRefreshToken(this@BangumiAuthActivity, token.refresh_token)
                UserPrefs.setBangumiUserId(this@BangumiAuthActivity, token.user_id)
                ThemeUtils.isChanged = true
                Toast.makeText(this@BangumiAuthActivity, "授权成功！用户 ID: ${token.user_id}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@BangumiAuthActivity, "授权失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }
}
