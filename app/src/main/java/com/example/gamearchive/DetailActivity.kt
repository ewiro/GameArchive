package com.example.gamearchive

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

@Suppress("DEPRECATION")
class DetailActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeUtils.applyTheme(this)

        val appId = intent.getIntExtra("APP_ID", 0)
        val appName = intent.getStringExtra("APP_NAME") ?: "Unknown"
        val price = intent.getStringExtra("APP_PRICE") ?: ""

        setContent {
            MiuixThemeForApp {
                DetailScreen(
                    appId = appId,
                    appName = appName,
                    price = price,
                    onBack = {
                        finish()
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    }
                )
            }
        }
    }
}
