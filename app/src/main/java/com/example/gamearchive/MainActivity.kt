package com.example.gamearchive

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {
    companion object {
        val ownedGameIds = mutableSetOf<Int>()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeUtils.applyTheme(this)
        LocaleHelper.currentApiLanguage = LocaleHelper.getApiLanguage(this)

        setContent {
            var settingsVersion by remember { mutableIntStateOf(0) }
            val lifecycleOwner = LocalLifecycleOwner.current
            val context = LocalContext.current

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        (context as? android.app.Activity)?.let {
                            ThemeUtils.applyStatusBarAppearance(it)
                        }
                        if (ThemeUtils.isChanged) {
                            if (ThemeUtils.hasLanguageChanged(context)) {
                                ThemeUtils.markLanguageApplied(context)
                                ThemeUtils.isChanged = false
                                (context as? android.app.Activity)?.recreate()
                                return@LifecycleEventObserver
                            }
                            ThemeUtils.isChanged = false
                            LocaleHelper.currentApiLanguage = LocaleHelper.getApiLanguage(context)
                            settingsVersion++
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            key(settingsVersion) {
                MiuixThemeForApp { MainScreen() }
            }
        }
    }
}
