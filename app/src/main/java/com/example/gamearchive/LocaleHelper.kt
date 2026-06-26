package com.example.gamearchive

import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * 管理应用语言切换。
 * 支持三种模式：跟随系统、中文、英文。
 */
object LocaleHelper {

    const val LANG_FOLLOW_SYSTEM = 0
    const val LANG_CHINESE = 1
    const val LANG_ENGLISH = 2

    /** 供 ViewModel 等无 Context 的地方使用，MainActivity 启动时赋值 */
    var currentApiLanguage = "schinese"

    /**
     * 在 Activity.attachBaseContext 中调用，
     * 返回一个包装了目标语言环境的 Context。
     */
    fun setLocale(context: Context): Context {
        val lang = ThemeUtils.getLanguage(context)
        return when (lang) {
            LANG_CHINESE -> setLocaleForLanguage(context, "zh")
            LANG_ENGLISH -> setLocaleForLanguage(context, "en")
            else -> context // 跟随系统，不干预
        }
    }

    private fun setLocaleForLanguage(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = context.resources.configuration.let {
            val c = android.content.res.Configuration(it)
            c.setLocale(locale)
            c
        }
        return context.createConfigurationContext(config)
    }

    /** 返回 API 请求应使用的 Steam 语言代码 */
    fun getApiLanguage(context: Context): String {
        val lang = ThemeUtils.getLanguage(context)
        return when (lang) {
            LANG_CHINESE -> "schinese"
            LANG_ENGLISH -> "english"
            else -> {
                // 跟随系统：判断系统语言
                val sysLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.resources.configuration.locales[0]
                } else {
                    @Suppress("DEPRECATION")
                    context.resources.configuration.locale
                }
                if (sysLocale.language == Locale.CHINESE.language) "schinese" else "english"
            }
        }
    }

    /** 返回 API 请求应使用的国家代码（始终为中国，保留人民币价格） */
    fun getApiCountry(): String = "cn"
}
