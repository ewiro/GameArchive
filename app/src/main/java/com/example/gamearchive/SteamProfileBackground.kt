package com.example.gamearchive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.PowerManager
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import java.io.File
import kotlinx.coroutines.CancellationException

@Composable
fun SteamProfileBackground(
    decor: SteamProfileDecor,
    playMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val media by produceState<SteamProfilePreparedMedia?>(
        initialValue = null,
        key1 = decor.backgroundMp4Url,
        key2 = decor.backgroundWebmUrl
    ) {
        value = try {
            SteamProfileDecorRepository.prepareBackground(context, decor)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
    val motionAllowed = rememberSteamProfileMotionAllowed(playMotion)

    Box(modifier) {
        media?.posterFile?.let { poster ->
            Image(
                painter = rememberAsyncImagePainter(poster),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        media?.videoFile?.let { video ->
            if (motionAllowed) {
                AndroidView(
                    factory = { SteamProfileTextureView(it) },
                    update = { it.setMedia(video, true) },
                    onRelease = SteamProfileTextureView::release,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun rememberSteamProfileMotionAllowed(requested: Boolean): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember(context) {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var powerSave by remember { mutableStateOf(powerManager.isPowerSaveMode) }

    DisposableEffect(context, lifecycleOwner, powerManager) {
        val lifecycleObserver = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                powerSave = powerManager.isPowerSaveMode
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        @Suppress("DEPRECATION")
        runCatching {
            context.registerReceiver(
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            )
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return requested && resumed && !powerSave
}

private class SteamProfileTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var targetSurface: Surface? = null
    private var mediaPath: String? = null
    private var playRequested = false
    private var prepared = false
    private var videoWidth = 0
    private var videoHeight = 0

    init {
        surfaceTextureListener = this
        isOpaque = false
        alpha = 0f
    }

    fun setMedia(file: File, play: Boolean) {
        playRequested = play
        val path = file.absolutePath
        if (mediaPath != path) {
            mediaPath = path
            createPlayerIfPossible()
        } else {
            updatePlayback()
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        targetSurface?.release()
        targetSurface = Surface(texture)
        createPlayerIfPossible()
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        applyCenterCrop()
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        releasePlayer()
        targetSurface?.release()
        targetSurface = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    fun release() {
        animate().cancel()
        releasePlayer()
        targetSurface?.release()
        targetSurface = null
        mediaPath = null
        alpha = 0f
    }

    private fun createPlayerIfPossible() {
        val path = mediaPath ?: return
        val surface = targetSurface ?: return
        releasePlayer()
        animate().cancel()
        alpha = 0f
        prepared = false
        player = runCatching {
            MediaPlayer().apply {
                isLooping = true
                setVolume(0f, 0f)
                setSurface(surface)
                setDataSource(path)
                setOnVideoSizeChangedListener { _, width, height ->
                    this@SteamProfileTextureView.videoWidth = width
                    this@SteamProfileTextureView.videoHeight = height
                    applyCenterCrop()
                }
                setOnPreparedListener {
                    prepared = true
                    updatePlayback()
                }
                setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        animate().alpha(1f).setDuration(180L).start()
                    }
                    false
                }
                setOnErrorListener { _, _, _ ->
                    animate().cancel()
                    alpha = 0f
                    releasePlayer()
                    true
                }
                prepareAsync()
            }
        }.getOrNull()
    }

    private fun updatePlayback() {
        val current = player ?: return
        if (!prepared) return
        runCatching {
            if (playRequested) {
                if (!current.isPlaying) current.start()
            } else if (current.isPlaying) {
                current.pause()
            }
        }.onFailure {
            animate().cancel()
            alpha = 0f
            releasePlayer()
        }
    }

    private fun applyCenterCrop() {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val viewRatio = width.toFloat() / height.toFloat()
        val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
        val scaleX = if (videoRatio > viewRatio) videoRatio / viewRatio else 1f
        val scaleY = if (videoRatio > viewRatio) 1f else viewRatio / videoRatio
        setTransform(Matrix().apply {
            setScale(scaleX, scaleY, width / 2f, height / 2f)
        })
    }

    private fun releasePlayer() {
        prepared = false
        player?.let { runCatching { it.release() } }
        player = null
    }
}
