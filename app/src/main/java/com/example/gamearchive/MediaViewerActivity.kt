package com.example.gamearchive

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

class MediaViewerActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.setLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val urls = intent.getStringArrayListExtra("URLS") ?: arrayListOf()
        val types = intent.getStringArrayListExtra("TYPES") ?: arrayListOf()
        val startIndex = intent.getIntExtra("INDEX", 0)

        setContent {
            MediaViewerScreen(
                urls = urls,
                types = types,
                startIndex = startIndex,
                onClose = { finish() }
            )
        }
    }
}

@Composable
private fun MediaViewerScreen(
    urls: List<String>,
    types: List<String>,
    startIndex: Int,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { urls.size }, initialPage = startIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 媒体翻页
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val url = urls[page]
            val type = types.getOrElse(page) { "image" }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (type == "image") {
                    // 图片：AsyncImage 填满屏幕
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // 视频：AndroidView 包装 VideoView
                    MediaVideoPlayer(url = url)
                }
            }
        }

        // 关闭按钮
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .size(DesignTokens.IconPlay)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Image(
                imageVector = MiuixIcons.Demibold.Close,
                contentDescription = "Close",
                modifier = Modifier.size(DesignTokens.IconHuge),
                colorFilter = ColorFilter.tint(Color.White)
            )
        }
    }
}

@Composable
private fun MediaVideoPlayer(url: String) {
    var isLoading by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setVideoURI(Uri.parse(url))
                    setOnPreparedListener { mp: MediaPlayer ->
                        isLoading = false
                        mp.start()
                    }
                    setOnErrorListener { _, _, _ ->
                        isLoading = false
                        false
                    }
                    videoViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(
                modifier = Modifier.size(DesignTokens.IconPlay),
                color = Color.White
            )
        }
    }

    // 离开页面时停止播放
    DisposableEffect(Unit) {
        onDispose {
            videoViewRef?.stopPlayback()
        }
    }
}
