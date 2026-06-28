package com.example.gamearchive

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class GameArchiveApp : Application(), ImageLoaderFactory {

    companion object {
        /** 全局共享的 Steam API 服务（带 15 秒超时，避免详情页加载卡住） */
        val apiService: SteamApiService by lazy {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            Retrofit.Builder()
                .baseUrl(AppConfig.PROXY_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SteamApiService::class.java)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
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
