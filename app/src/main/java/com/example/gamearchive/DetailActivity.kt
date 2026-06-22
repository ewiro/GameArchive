package com.example.gamearchive

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {

    private val mediaList = mutableListOf<MediaItem>()

    data class MediaItem(val url: String, val thumbnailUrl: String, val isVideo: Boolean)

    private lateinit var tvHistoryLow: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeUtils.applyTheme(this)
        setContentView(R.layout.activity_detail)

        // 1. 获取 AppBar 和 Toolbar
        val appBar = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBar)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)

        // 2. 手动处理状态栏高度，确保 AppBar 完整滑动
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Toolbar 用 wrap_content + minHeight，这里加的 top padding 会撑高而不再裁切标题
            toolbar.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // 3. 彻底关闭 AppBar 滚动时的动态染色 (Material You 残留)
        appBar.setLiftOnScroll(false)
        appBar.isLiftOnScroll = false
        appBar.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.list_page_bg))
        appBar.outlineProvider = null
        androidx.core.view.ViewCompat.setElevation(appBar, 0f)

        val appId = intent.getIntExtra("APP_ID", 0)
        val appName = intent.getStringExtra("APP_NAME") ?: "Unknown"
        val price = intent.getStringExtra("APP_PRICE") ?: ""

        val tvName = findViewById<TextView>(R.id.tvDetailName)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        val tvFinalPrice = findViewById<TextView>(R.id.tvFinalPrice)
        val tvOriginalPrice = findViewById<TextView>(R.id.tvOriginalPrice)
        val tvDiscount = findViewById<TextView>(R.id.tvDiscount)
        tvHistoryLow = findViewById(R.id.tvHistoryLow)

        val tvRelease = findViewById<TextView>(R.id.tvReleaseDate)
        val tvDeveloper = findViewById<TextView>(R.id.tvDeveloper)

        val wvDesc = findViewById<WebView>(R.id.wvDescription)
        val progressBar = findViewById<ProgressBar>(R.id.detailProgressBar)
        val rvScreenshots = findViewById<RecyclerView>(R.id.rvScreenshots)

        val tvReviewSummary = findViewById<TextView>(R.id.tvReviewSummary)
        val tvReviewPercent = findViewById<TextView>(R.id.tvReviewPercent)
        val tvReviewCount = findViewById<TextView>(R.id.tvReviewCount)
        val rvReviews = findViewById<RecyclerView>(R.id.rvReviews)
        rvReviews.layoutManager = LinearLayoutManager(this)

        tvName.text = appName
        tvFinalPrice.text = price
        btnBack.setOnClickListener { finish() }

        wvDesc.setBackgroundColor(0)
        wvDesc.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            mediaPlaybackRequiresUserGesture = false
        }

        mediaList.clear()
        setupMediaAdapter(rvScreenshots)

        if (appId != 0) {
            fetchGameDetails(appId, progressBar, wvDesc, tvRelease, rvScreenshots, tvFinalPrice, tvOriginalPrice, tvDiscount, tvDeveloper, tvName)
            fetchGameReviews(appId, tvReviewSummary, tvReviewPercent, tvReviewCount, rvReviews)
        }
    }

    private fun setupMediaAdapter(rv: RecyclerView) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val gapTotal = (8 * displayMetrics.density).toInt()
        val imageWidth = screenWidth - gapTotal
        val targetHeight = (imageWidth * 9 / 16)

        rv.layoutParams.height = targetHeight
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val snapHelper = androidx.recyclerview.widget.PagerSnapHelper()
        snapHelper.attachToRecyclerView(rv)

        rv.setPadding(0, 0, 0, 0)
        while (rv.itemDecorationCount > 0) rv.removeItemDecorationAt(0)

        rv.adapter = ScreenshotAdapter(mediaList) { position ->
            val intent = Intent(this, MediaViewerActivity::class.java)
            intent.putStringArrayListExtra("URLS", ArrayList(mediaList.map { it.url }))
            intent.putStringArrayListExtra("TYPES", ArrayList(mediaList.map { if (it.isVideo) "video" else "image" }))
            intent.putExtra("INDEX", position)
            startActivity(intent)
        }
    }

    private fun fetchGameDetails(
        appId: Int, progressBar: ProgressBar, wvDesc: WebView, tvRelease: TextView,
        rvScreenshots: RecyclerView, tvFinalPrice: TextView, tvOriginalPrice: TextView, tvDiscount: TextView,
        tvDeveloper: TextView, tvName: TextView
    ) {
        val apiService = MainActivity.apiServiceGlobal

        lifecycleScope.launch {
            try {
                val response = apiService.getGameDetails(appId)
                val data = response[appId.toString()]?.data

                if (data != null) {
                    if (!data.name.isNullOrEmpty()) {
                        tvName.text = data.name
                    }

                    mediaList.clear()

                    if (!data.movies.isNullOrEmpty()) {
                        data.movies.take(3).forEach { movie ->
                            var videoUrl = movie.mp4?.max ?: movie.mp4?.p480 ?: movie.webm?.max ?: movie.webm?.p480
                            if (videoUrl == null && movie.id != null) {
                                videoUrl = "https://cdn.cloudflare.steamstatic.com/steam/apps/${movie.id}/movie_max.mp4"
                            }
                            val thumbUrl = movie.thumbnail
                            if (videoUrl != null && thumbUrl != null) {
                                mediaList.add(MediaItem(videoUrl, thumbUrl, true))
                            }
                        }
                    }

                    if (!data.screenshots.isNullOrEmpty()) {
                        data.screenshots.take(10).forEach { shot ->
                            val fullUrl = shot.path_full
                            val thumbUrl = shot.path_thumbnail
                            if (fullUrl != null && thumbUrl != null) {
                                mediaList.add(MediaItem(fullUrl, thumbUrl, false))
                            }
                        }
                    }

                    rvScreenshots.adapter?.notifyDataSetChanged()

                    val devs = data.developers?.firstOrNull() ?: "未知"
                    tvDeveloper.text = devs
                    tvRelease.text = "${data.release_date?.date ?: "未知"}"

                    val priceInfo = data.price_overview
                    if (priceInfo != null) {
                        if ((priceInfo.discount_percent ?: 0) > 0) {
                            tvDiscount.visibility = View.VISIBLE
                            tvDiscount.text = "-${priceInfo.discount_percent}%"

                            tvOriginalPrice.visibility = View.VISIBLE
                            tvOriginalPrice.text = priceInfo.initial_formatted
                            tvOriginalPrice.paintFlags = tvOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

                            tvFinalPrice.text = priceInfo.final_formatted
                            tvFinalPrice.textSize = 18f
                        } else {
                            tvDiscount.visibility = View.GONE
                            tvOriginalPrice.visibility = View.GONE
                            tvFinalPrice.text = priceInfo.final_formatted
                            tvFinalPrice.textSize = 22f
                        }
                    }

                    var rawDesc = data.detailed_description ?: data.short_description ?: ""
                    rawDesc = rawDesc.replace(Regex("(?i)<p[^>]*>\\s*&nbsp;\\s*</p>"), "")
                    rawDesc = rawDesc.replace(Regex("(?i)<p[^>]*>\\s*</p>"), "")

                    val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    val bodyColor = if (isNight) "#D8D8D8" else "#333333"
                    val headColor = if (isNight) "#F2F2F2" else "#000000"
                    val linkColor = if (isNight) "#5E9BFF" else "#3482FF"

                    val css = """
                        <style>
                            * { margin: 0; padding: 0; box-sizing: border-box; max-width: 100%; }
                            body { background-color: transparent; color: $bodyColor; font-family: sans-serif; width: 100vw; overflow-x: hidden; font-size: 15px !important; line-height: 1.6 !important; }
                            img, video { display: block !important; max-width: 100% !important; width: auto !important; height: auto !important; border: 0 !important; margin: 0 !important; }
                            a { color: $linkColor; text-decoration: none; font-weight: bold; }
                            h1, h2, h3 { margin: 24px 0 12px 0 !important; font-weight: bold; line-height: 1.4 !important; color: $headColor; font-size: 18px !important; }
                            p { margin-bottom: 12px !important; }
                            ul, ol { margin-left: 20px !important; margin-bottom: 12px !important; }
                        </style>
                    """.trimIndent()

                    val jsScript = """
                        <script>
                            document.addEventListener("DOMContentLoaded", function() {
                                var elements = document.body.querySelectorAll('p, div, span, a, h1, h2, h3, li');
                                for (var i = 0; i < elements.length; i++) {
                                    var el = elements[i];
                                    var hasImg = el.querySelector('img') || el.querySelector('video');
                                    var textContent = el.innerText.replace(/\s/g, ''); 
                                    var hasText = textContent.length > 0;
                                    if (hasImg) {
                                        if (!hasText) {
                                            el.style.margin = '0'; el.style.padding = '0'; el.style.lineHeight = '0'; el.style.fontSize = '0'; el.style.display = 'block';
                                        } else {
                                            el.style.display = 'block';
                                        }
                                    }
                                }
                                var bbSpans = document.querySelectorAll('.bb_img_ctn');
                                for (var j = 0; j < bbSpans.length; j++) { bbSpans[j].style.display = 'block'; bbSpans[j].style.lineHeight = '0'; bbSpans[j].style.margin = '0'; }
                            });
                        </script>
                    """.trimIndent()

                    val finalHtml = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\">$css</head><body>$rawDesc $jsScript</body></html>"
                    wvDesc.loadDataWithBaseURL(null, finalHtml, "text/html", "utf-8", null)

                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
            }
        }
    }


    private fun fetchGameReviews(appId: Int, tvSummary: TextView, tvPercent: TextView, tvCount: TextView, rvReviews: RecyclerView) {
        val apiService = MainActivity.apiServiceGlobal

        lifecycleScope.launch {
            try {
                val response = apiService.getGameReviews(appId,count = 50)
                val summary = response.query_summary

                if (summary != null && summary.total_reviews > 0) {
                    val rate = (summary.total_positive.toDouble() / summary.total_reviews.toDouble() * 100).toInt()

                    tvSummary.text = summary.review_score_desc ?: "暂无评价"
                    tvPercent.text = "$rate%"
                    tvCount.text = "(${summary.total_reviews} 篇)"

                    val color = if (rate >= 95) 0xFFE65100.toInt()
                    else if (rate >= 70) 0xFF1565C0.toInt()
                    else if (rate >= 40) 0xFF616161.toInt()
                    else 0xFFD32F2F.toInt()

                    tvSummary.setTextColor(color)
                } else {
                    tvSummary.text = "暂无数据"
                    tvPercent.text = "--%"
                }

                val reviews = response.reviews
                if (!reviews.isNullOrEmpty()) {
                    rvReviews.adapter = ReviewAdapter(reviews)
                    rvReviews.visibility = View.VISIBLE
                } else {
                    rvReviews.visibility = View.GONE
                }

            } catch (e: Exception) {
                tvSummary.text = "加载失败"
            }
        }
    }

    // 画廊适配器
    class ScreenshotAdapter(private val items: List<MediaItem>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<ScreenshotAdapter.ViewHolder>() {

        class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.ivScreenshot)
            val overlay: android.view.View = view.findViewById(R.id.vVideoOverlay)
            val playIcon: ImageView = view.findViewById(R.id.ivPlayIcon)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) = ViewHolder(
            android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_screenshot, parent, false)
        ).apply {
            itemView.layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.image.load(item.thumbnailUrl) { crossfade(true) }
            if (item.isVideo) {
                holder.overlay.visibility = android.view.View.VISIBLE
                holder.playIcon.visibility = android.view.View.VISIBLE
            } else {
                holder.overlay.visibility = android.view.View.GONE
                holder.playIcon.visibility = android.view.View.GONE
            }
            holder.itemView.setOnClickListener { onClick(position) }
        }
        override fun getItemCount() = items.size
    }

    // 评论适配器
    class ReviewAdapter(private val reviews: List<SteamReview>) : RecyclerView.Adapter<ReviewAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvVoteState: TextView = view.findViewById(R.id.tvVoteState)
            val ivVoteIcon: ImageView = view.findViewById(R.id.ivVoteIcon)
            val tvPlaytime: TextView = view.findViewById(R.id.tvPlaytime)
            val tvDate: TextView = view.findViewById(R.id.tvDate)
            val tvContent: TextView = view.findViewById(R.id.tvContent)
            val tvHelpful: TextView = view.findViewById(R.id.tvHelpful)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = reviews[position]

            if (item.voted_up) {
                holder.tvVoteState.text = "推荐"
                holder.tvVoteState.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                holder.ivVoteIcon.setColorFilter(android.graphics.Color.parseColor("#4CAF50"))
                holder.ivVoteIcon.setImageResource(R.drawable.ic_thumb_up)
            } else {
                holder.tvVoteState.text = "不推荐"
                holder.tvVoteState.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
                holder.ivVoteIcon.setColorFilter(android.graphics.Color.parseColor("#D32F2F"))
                holder.ivVoteIcon.setImageResource(R.drawable.ic_thumb_down)
            }

            val hours = item.author.playtime_forever / 60
            holder.tvPlaytime.text = "总时数 $hours 小时"

            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date(item.timestamp_created * 1000))
            holder.tvDate.text = date

            var content = item.review.replace(Regex("\\[.*?\\]"), "")
            holder.tvContent.text = content

            if (item.votes_up > 0) {
                holder.tvHelpful.visibility = View.VISIBLE
                holder.tvHelpful.text = "${item.votes_up} 人觉得有用"
            } else {
                holder.tvHelpful.visibility = View.GONE
            }
        }
        override fun getItemCount() = reviews.size
    }
}