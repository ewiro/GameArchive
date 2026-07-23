package com.example.gamearchive

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.*
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

class BangumiDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val subjectId = intent.getIntExtra("SUBJECT_ID", 0)
        val subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        val subjectNameCn = intent.getStringExtra("SUBJECT_NAME_CN") ?: ""
        val subjectImage = intent.getStringExtra("SUBJECT_IMAGE") ?: ""

        setContent {
            MiuixThemeForApp {
                BangumiDetailScreen(
                    subjectId = subjectId,
                    subjectName = subjectName,
                    subjectNameCn = subjectNameCn,
                    subjectImage = subjectImage,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun BangumiDetailScreen(
    subjectId: Int,
    subjectName: String,
    subjectNameCn: String,
    subjectImage: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val statusBarDp = statusBarHeightDp()

    var detail by remember { mutableStateOf<BangumiSubjectDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(subjectId) {
        try {
            detail = GameArchiveApp.bgmService.getSubject(subjectId)
        } catch (_: Exception) {
            Toast.makeText(context, "加载失败", Toast.LENGTH_SHORT).show()
        }
        isLoading = false
    }

    val displayName = subjectNameCn.ifEmpty { subjectName }
    val score = extractScore(detail?.rating)
    val dim = MiuixTheme.colorScheme.onSurface.copy(alpha = DesignTokens.OpacityBody)

    Box(Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶栏
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = statusBarDp + 4.dp, end = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Image(
                            imageVector = MiuixIcons.Demibold.Back,
                            contentDescription = "Back",
                            modifier = Modifier.size(DesignTokens.IconXl),
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                        )
                    }
                    Text(
                        text = displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = DesignTokens.TextHeadline.sp,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }

                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载中...", color = dim)
                    }
                } else if (detail != null) {
                    val d = detail!!
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // 封面 + 基本信息
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // 封面
                            Box(
                                modifier = Modifier
                                    .size(width = 120.dp, height = 168.dp)
                                    .clip(RoundedCornerShape(DesignTokens.CornerLarge))
                            ) {
                                val coverUrl = d.images?.large ?: d.images?.common ?: subjectImage
                                if (coverUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(coverUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            // 信息
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = d.name ?: subjectName,
                                    fontSize = DesignTokens.TextTitle.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!d.name_cn.isNullOrEmpty() && d.name_cn != d.name) {
                                    Text(
                                        text = d.name_cn,
                                        fontSize = DesignTokens.TextBody1.sp,
                                        color = dim,
                                        maxLines = 1
                                    )
                                }
                                Spacer(Modifier.height(8.dp))

                                // 评分
                                if (score != null && score > 0) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = String.format("%.1f", score),
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = bangumiScoreColor(score)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = bangumiGrade(score),
                                            fontSize = 12.sp,
                                            color = dim
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }

                                // 日期 + 话数
                                if (!d.date.isNullOrEmpty()) {
                                    Text(
                                        text = "开播 ${d.date}",
                                        fontSize = DesignTokens.TextBody1.sp,
                                        color = dim
                                    )
                                }
                                if (d.total_episodes != null && d.total_episodes!! > 0) {
                                    Text(
                                        text = "共 ${d.total_episodes} 话",
                                        fontSize = DesignTokens.TextBody1.sp,
                                        color = dim
                                    )
                                }
                                if (d.eps != null && d.eps!! > 0) {
                                    Text(
                                        text = "已播 ${d.eps} 话",
                                        fontSize = DesignTokens.TextBody1.sp,
                                        color = dim
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // 标签
                        if (!d.tags.isNullOrEmpty()) {
                            Text(
                                text = "标签",
                                fontSize = DesignTokens.TextSubtitle.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                d.tags!!.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(DesignTokens.CornerMedium))
                                            .background(MiuixTheme.colorScheme.secondaryContainer)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = tag.name,
                                            fontSize = 12.sp,
                                            color = dim
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // 简介
                        if (!d.summary.isNullOrEmpty()) {
                            Text(
                                text = "简介",
                                fontSize = DesignTokens.TextSubtitle.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            // WebView 渲染简介
                            val isDarkWebView = isSystemInDarkTheme()
                            AndroidView(
                                factory = { ctx ->
                                    android.webkit.WebView(ctx).apply {
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                                        )
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        isVerticalScrollBarEnabled = false
                                        settings.javaScriptEnabled = false
                                        val cssText = if (isDarkWebView) "#CCCCCC" else "#333333"
                                        val html = """
                                            <html><head><style>
                                                body { font-size: 14px; line-height:1.6; color:$cssText; background:transparent;
                                                    padding:8px 0; margin:0; font-family:sans-serif; text-indent:1em; }
                                                a { color:#3482FF; }
                                            </style></head><body>${d.summary}</body></html>
                                        """.trimIndent()
                                        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 100.dp)
                            )
                        }

                        // 详情
                        if (!d.infobox.isNullOrEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "详情",
                                fontSize = DesignTokens.TextSubtitle.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            d.infobox!!.forEach { item ->
                                val v = when (item.value) {
                                    is String -> item.value as String
                                    is Number -> item.value.toString()
                                    else -> ""
                                }
                                if (v.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = item.key,
                                            fontSize = 13.sp,
                                            color = dim,
                                            modifier = Modifier.width(72.dp)
                                        )
                                        Text(
                                            text = v,
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(40.dp))
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载失败", color = dim)
                    }
                }
            }
        }
    }
}

private fun extractScore(r: Any?): Double? = when (r) {
    is Map<*, *> -> {
        val s = r["score"]
        when (s) { is Double -> s; is Number -> s.toDouble(); else -> null }
    }
    else -> null
}

private fun bangumiScoreColor(score: Double): Color = when {
    score >= 8.0 -> Color(0xFFE74C3C)
    score >= 7.0 -> Color(0xFFE67E22)
    score >= 5.0 -> Color(0xFF3498DB)
    else -> Color(0xFF999999)
}

private fun bangumiGrade(score: Double): String = when {
    score >= 9.0 -> "超神作"
    score >= 8.5 -> "神作"
    score >= 8.0 -> "力荐"
    score >= 7.5 -> "推荐"
    score >= 7.0 -> "不错"
    score >= 6.0 -> "还行"
    score >= 5.0 -> "一般"
    else -> "较差"
}
