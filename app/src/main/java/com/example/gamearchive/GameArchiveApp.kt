package com.example.gamearchive

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class GameArchiveApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
    }

    companion object {
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
