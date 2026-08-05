package com.example.gamearchive

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class GameArchiveApp : Application(), ImageLoaderFactory {

    companion object {
        @Volatile
        private var authenticatedToken: String? = null
        @Volatile
        private var authenticatedBgmService: BangumiCollectionService? = null
        /** 全局共享 OkHttpClient（30s 超时，兼顾特惠页） */
        val okHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }

        /** 全局共享的 Steam API 服务 */
        val apiService: SteamApiService by lazy {
            Retrofit.Builder()
                .baseUrl(AppConfig.PROXY_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SteamApiService::class.java)
        }

        /** 全局共享的 Bangumi API 服务 */
        val bgmService: BangumiService by lazy {
            Retrofit.Builder()
                .baseUrl(AppConfig.PROXY_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BangumiService::class.java)
        }

        /** Bangumi OAuth 服务（通过公共代理转发到 bgm.tv） */
        val bgmOAuthService: BangumiOAuthService by lazy {
            Retrofit.Builder()
                .baseUrl(AppConfig.PROXY_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BangumiOAuthService::class.java)
        }

        /** 创建带 Bearer Token 的 Bangumi 收藏编辑服务（走代理） */
        @Synchronized
        fun createAuthenticatedBgmService(token: String): BangumiCollectionService {
            if (authenticatedToken == token) {
                authenticatedBgmService?.let { return it }
            }
            val authClient = okHttpClient.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/json")
                        .header("User-Agent", "GameArchive/${BuildConfig.VERSION_NAME} (Android)")
                        .build())
                }
                .build()
            return Retrofit.Builder()
                .baseUrl(AppConfig.PROXY_URL)
                .client(authClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BangumiCollectionService::class.java)
                .also {
                    authenticatedToken = token
                    authenticatedBgmService = it
                }
        }

        @Synchronized
        fun clearAuthenticatedBgmService() {
            authenticatedToken = null
            authenticatedBgmService = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        UserPrefs.migrateCredentials(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_images"))
                    .maxSizeBytes(50 * 1024 * 1024)  // 50 MB
                    .build()
            }
            .components {
                // GIF 动图支持：API 28+ 用 ImageDecoder，低版本用 Coil 内置 GifDecoder
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .build()
    }
}
