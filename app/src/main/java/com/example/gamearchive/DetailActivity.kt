package com.example.gamearchive

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri

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
                    onOpenStore = { openSteamStore(appId) },
                    onBack = {
                        finish()
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    }
                )
            }
        }
    }

    private fun openSteamStore(appId: Int) {
        val storeUrl = "https://store.steampowered.com/app/$appId/"
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, storeUrl.toUri())
                    .setPackage("com.valvesoftware.android.steam.community")
            )
        } catch (_: ActivityNotFoundException) {
            openExternalWebLink(this, storeUrl)
        } catch (_: SecurityException) {
            openExternalWebLink(this, storeUrl)
        }
    }
}
