package com.example.gamearchive

import androidx.core.content.edit
import androidx.core.graphics.scale
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request

data class SteamProfileDecor(
    val avatarUrl: String?,
    val avatarFrameUrl: String?,
    val backgroundMp4Url: String?,
    val backgroundWebmUrl: String?
)

data class SteamProfilePreparedMedia(
    val videoFile: File,
    val posterFile: File?
)

/**
 * Steam 社区 mini-profile 装扮数据与视频缓存。
 *
 * 只通过项目公共代理访问；所有返回的媒体地址都必须属于 steamstatic.com。
 */
object SteamProfileDecorRepository {
    private const val PREF_NAME = "steam_profile_decor"
    private const val STEAM_ID64_OFFSET = 76561197960265728L
    private const val METADATA_TTL_MS = 12L * 60L * 60L * 1000L
    private const val MAX_SINGLE_VIDEO_BYTES = 12L * 1024L * 1024L
    private const val MAX_VIDEO_CACHE_BYTES = 64L * 1024L * 1024L
    private const val POSTER_MAX_WIDTH = 768

    private val accountLocks = ConcurrentHashMap<String, Mutex>()
    private val mediaLocks = ConcurrentHashMap<String, Mutex>()
    private val downloadSlots = Semaphore(2)
    private val cacheMutex = Mutex()

    suspend fun load(
        context: Context,
        steamId: String,
        forceRefresh: Boolean = false
    ): SteamProfileDecor? {
        val accountId = steamIdToAccountId(steamId) ?: return null
        val appContext = context.applicationContext
        return accountLocks.getOrPut(steamId) { Mutex() }.withLock {
            val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val cached = readMetadata(prefs, steamId)
            val now = System.currentTimeMillis()
            val cacheIsFresh = cached != null &&
                now >= cached.fetchedAt &&
                now - cached.fetchedAt < METADATA_TTL_MS
            if (!forceRefresh && cacheIsFresh) return@withLock cached.decor

            val fetched = withContext(Dispatchers.IO) {
                runCatchingCancellable {
                    parse(GameArchiveApp.apiService.getSteamMiniProfile(accountId))
                }.getOrNull()
            }
            if (fetched != null) {
                writeMetadata(prefs, steamId, fetched, now)
                fetched
            } else {
                cached?.decor
            }
        }
    }

    suspend fun prepareBackground(
        context: Context,
        decor: SteamProfileDecor
    ): SteamProfilePreparedMedia? {
        val candidates = listOfNotNull(
            decor.backgroundMp4Url,
            decor.backgroundWebmUrl
        ).distinct()
        for (url in candidates) {
            if (!isAllowedSteamMediaUrl(url)) continue
            val prepared = prepareMedia(context.applicationContext, url)
            if (prepared != null) return prepared
        }
        return null
    }

    fun proxiedMediaUrl(originalUrl: String?): String? {
        val url = originalUrl?.takeIf(::isAllowedSteamMediaUrl) ?: return null
        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.name())
        return "${AppConfig.PROXY_URL.trimEnd('/')}/steam-media?url=$encodedUrl"
    }

    private suspend fun prepareMedia(
        context: Context,
        originalUrl: String
    ): SteamProfilePreparedMedia? = downloadSlots.withPermit {
        withContext(Dispatchers.IO) {
            val hash = sha256(originalUrl)
            mediaLocks.getOrPut(hash) { Mutex() }.withLock {
                val cacheDir = File(context.cacheDir, "steam_profile_backgrounds").apply {
                    mkdirs()
                }
                val extension = runCatching {
                    URI(originalUrl).path.substringAfterLast('.', "mp4").lowercase()
                }.getOrDefault("mp4").takeIf { it == "mp4" || it == "webm" } ?: "mp4"
                val videoFile = File(cacheDir, "video_${hash}.$extension")
                val posterFile = File(cacheDir, "poster_${hash}_768.webp")

                if (!videoFile.isFile || videoFile.length() !in 1..MAX_SINGLE_VIDEO_BYTES) {
                    videoFile.delete()
                    if (!downloadVideo(originalUrl, videoFile)) return@withContext null
                }
                videoFile.setLastModified(System.currentTimeMillis())

                val poster = when {
                    posterFile.isFile && posterFile.length() > 0L -> posterFile
                    createPoster(videoFile, posterFile) -> posterFile
                    else -> null
                }
                poster?.setLastModified(System.currentTimeMillis())

                cacheMutex.withLock {
                    pruneCache(cacheDir, listOfNotNull(videoFile, poster).toSet())
                }
                SteamProfilePreparedMedia(videoFile, poster)
            }
        }
    }

    private suspend fun downloadVideo(originalUrl: String, destination: File): Boolean {
        val proxyUrl = proxiedMediaUrl(originalUrl) ?: return false
        val request = Request.Builder()
            .url(proxyUrl)
            .header("Accept", "video/mp4,video/webm,video/*;q=0.9,*/*;q=0.1")
            .get()
            .build()
        val partial = File(destination.parentFile, "${destination.name}.part")
        partial.delete()

        return runCatchingCancellable {
            GameArchiveApp.okHttpClient.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body ?: return@use false
                val declaredLength = body.contentLength()
                if (declaredLength > MAX_SINGLE_VIDEO_BYTES) return@use false
                val contentType = body.contentType()?.toString().orEmpty().lowercase()
                if (
                    contentType.isNotBlank() &&
                    !contentType.startsWith("video/") &&
                    !contentType.startsWith("application/octet-stream")
                ) {
                    return@use false
                }

                var copied = 0L
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied += read
                            if (copied > MAX_SINGLE_VIDEO_BYTES) {
                                throw IllegalStateException("Steam profile video exceeds limit")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                copied > 0L && partial.renameTo(destination)
            }
        }.getOrDefault(false).also { success ->
            if (!success) {
                partial.delete()
                destination.delete()
            }
        }
    }

    private fun createPoster(videoFile: File, posterFile: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(videoFile.absolutePath)
            val source = retriever.getFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.getFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@runCatching false
            val scaled = if (source.width > POSTER_MAX_WIDTH) {
                val height = (
                    source.height.toLong() * POSTER_MAX_WIDTH / source.width
                    ).toInt().coerceAtLeast(1)
                source.scale(POSTER_MAX_WIDTH, height)
            } else {
                source
            }
            posterFile.outputStream().buffered().use { output ->
                @Suppress("DEPRECATION")
                scaled.compress(Bitmap.CompressFormat.WEBP, 82, output)
            }
            if (scaled !== source) scaled.recycle()
            source.recycle()
            posterFile.length() > 0L
        }.getOrDefault(false).also { success ->
            if (!success) posterFile.delete()
            retriever.release()
        }
    }

    private fun pruneCache(directory: File, protectedFiles: Set<File>) {
        val files = directory.listFiles().orEmpty().filter(File::isFile)
        var total = files.sumOf(File::length)
        if (total <= MAX_VIDEO_CACHE_BYTES) return
        val protectedPaths = protectedFiles.mapTo(hashSetOf()) { it.absolutePath }
        files.sortedBy(File::lastModified).forEach { file ->
            if (total <= MAX_VIDEO_CACHE_BYTES) return
            if (file.absolutePath !in protectedPaths) {
                val size = file.length()
                if (file.delete()) total -= size
            }
        }
    }

    private fun parse(payload: JsonObject): SteamProfileDecor {
        val background = payload.getAsJsonObject("profile_background")
        return SteamProfileDecor(
            avatarUrl = payload.stringValue("avatar_url")
                ?.takeIf(::isAllowedSteamMediaUrl),
            avatarFrameUrl = payload.imageUrlValue("avatar_frame")
                ?.takeIf(::isAllowedSteamMediaUrl),
            backgroundMp4Url = background?.stringValue("video/mp4")
                ?.takeIf(::isAllowedSteamMediaUrl),
            backgroundWebmUrl = background?.stringValue("video/webm")
                ?.takeIf(::isAllowedSteamMediaUrl)
        )
    }

    private fun steamIdToAccountId(steamId: String): Long? {
        val steamId64 = steamId.trim().toLongOrNull() ?: return null
        return (steamId64 - STEAM_ID64_OFFSET).takeIf { it >= 0L }
    }

    private fun isAllowedSteamMediaUrl(raw: String): Boolean {
        return runCatching {
            val uri = URI(raw)
            val host = uri.host?.lowercase() ?: return@runCatching false
            uri.scheme.equals("https", ignoreCase = true) &&
                (host == "steamstatic.com" || host.endsWith(".steamstatic.com"))
        }.getOrDefault(false)
    }

    private fun readMetadata(
        prefs: android.content.SharedPreferences,
        steamId: String
    ): CachedDecor? {
        val fetchedAt = prefs.getLong("${steamId}_fetched_at", 0L)
        if (fetchedAt <= 0L) return null
        return CachedDecor(
            fetchedAt = fetchedAt,
            decor = SteamProfileDecor(
                avatarUrl = prefs.getString("${steamId}_avatar", null)
                    ?.takeIf(String::isNotBlank),
                avatarFrameUrl = prefs.getString("${steamId}_frame", null)
                    ?.takeIf(String::isNotBlank),
                backgroundMp4Url = prefs.getString("${steamId}_mp4", null)
                    ?.takeIf(String::isNotBlank),
                backgroundWebmUrl = prefs.getString("${steamId}_webm", null)
                    ?.takeIf(String::isNotBlank)
            )
        )
    }

    private fun writeMetadata(
        prefs: android.content.SharedPreferences,
        steamId: String,
        decor: SteamProfileDecor,
        fetchedAt: Long
    ) {
        prefs.edit {
                putLong("${steamId}_fetched_at", fetchedAt)
                .putString("${steamId}_avatar", decor.avatarUrl.orEmpty())
                .putString("${steamId}_frame", decor.avatarFrameUrl.orEmpty())
                .putString("${steamId}_mp4", decor.backgroundMp4Url.orEmpty())
                .putString("${steamId}_webm", decor.backgroundWebmUrl.orEmpty())
            }
    }

    private fun JsonObject.stringValue(key: String): String? {
        return (get(key) as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.asString
            ?.takeIf(String::isNotBlank)
    }

    private fun JsonObject.imageUrlValue(key: String): String? {
        return when (val value: JsonElement? = get(key)) {
            is JsonPrimitive -> value.takeIf { it.isString }?.asString
            is JsonObject -> listOf("image", "image_url", "url", "static")
                .firstNotNullOfOrNull { value.stringValue(it) }
            else -> null
        }
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private data class CachedDecor(
        val fetchedAt: Long,
        val decor: SteamProfileDecor
    )
}
