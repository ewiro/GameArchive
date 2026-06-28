package com.example.gamearchive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

class LoginActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (UserPrefs.isLoggedIn(this)) {
            startMainActivity()
            return
        }

        val colorSchemeMode = when (ThemeUtils.getThemeMode(this)) {
            0 -> top.yukonga.miuix.kmp.theme.ColorSchemeMode.Light
            1 -> top.yukonga.miuix.kmp.theme.ColorSchemeMode.Dark
            else -> top.yukonga.miuix.kmp.theme.ColorSchemeMode.System
        }

        setContent {
            MiuixTheme(
                controller = top.yukonga.miuix.kmp.theme.ThemeController(
                    colorSchemeMode = colorSchemeMode
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoginScreen(
                        onLoginSuccess = { startMainActivity() },
                        onHelpClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://steamcommunity.com/dev/apikey"))
                            startActivity(intent)
                        }
                    )
                }
            }
            } // close Surface
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}

@Composable
private fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onHelpClick: () -> Unit
) {
    val context = LocalContext.current
    var steamId by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 400.dp)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = context.getString(R.string.login_title),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = context.getString(R.string.login_subtitle),
            modifier = Modifier.padding(bottom = 48.dp)
        )

        // Steam ID 输入框 — label 当 placeholder 用
        TextField(
            value = steamId,
            onValueChange = { steamId = it },
            modifier = Modifier.fillMaxWidth(),
            label = context.getString(R.string.login_steam_id_hint),
            useLabelAsPlaceholder = true,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // API Key 输入框
        TextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = context.getString(R.string.login_api_key_hint),
            useLabelAsPlaceholder = true,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 登录按钮 — 浅色蓝底 / 深色灰底
        Box(
            modifier = Modifier
                .height(DesignTokens.ButtonHeight)
                .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                .background(buttonBgColor())
                .clickable {
                    if (steamId.isBlank() || apiKey.isBlank()) {
                        Toast.makeText(context, R.string.login_empty_fields, Toast.LENGTH_SHORT).show()
                        return@clickable
                    }
                    UserPrefs.saveCredentials(context, apiKey, steamId)
                    Toast.makeText(context, R.string.login_success, Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                }
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = context.getString(R.string.login_button),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = DesignTokens.TextBody1.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(
            text = context.getString(R.string.login_help),
            onClick = onHelpClick
        )
    }
}
