package com.example.gamearchive

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load

class MediaViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_viewer)

        // 获取传递过来的媒体数据
        val urls = intent.getStringArrayListExtra("URLS") ?: arrayListOf()
        val types = intent.getStringArrayListExtra("TYPES") ?: arrayListOf()
        val startIndex = intent.getIntExtra("INDEX", 0)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val btnClose = findViewById<View>(R.id.btnClose)

        // 设置适配器并跳转到用户点击的位置
        viewPager.adapter = MediaPagerAdapter(urls, types)
        viewPager.setCurrentItem(startIndex, false)

        btnClose.setOnClickListener { finish() }
    }

    // 媒体翻页适配器，支持图片和视频
    class MediaPagerAdapter(private val urls: List<String>, private val types: List<String>) :
        RecyclerView.Adapter<MediaPagerAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivImage: ImageView = view.findViewById(R.id.ivFullImage)
            val videoView: VideoView = view.findViewById(R.id.videoView)
            val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_media_page, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val url = urls[position]
            val type = types[position]

            if (type == "image") {
                // 图片模式：显示ImageView，隐藏VideoView
                holder.ivImage.visibility = View.VISIBLE
                holder.videoView.visibility = View.GONE
                holder.progressBar.visibility = View.GONE
                holder.ivImage.load(url)
            } else {
                // 视频模式：显示VideoView，加载时显示进度条
                holder.ivImage.visibility = View.GONE
                holder.videoView.visibility = View.VISIBLE
                holder.progressBar.visibility = View.VISIBLE

                val mediaController = MediaController(holder.itemView.context)
                mediaController.setAnchorView(holder.videoView)
                holder.videoView.setMediaController(mediaController)
                holder.videoView.setVideoURI(Uri.parse(url))

                holder.videoView.setOnPreparedListener { mp ->
                    holder.progressBar.visibility = View.GONE
                    mp.start() // 准备就绪后自动播放
                }
                holder.videoView.setOnErrorListener { _, _, _ ->
                    holder.progressBar.visibility = View.GONE
                    false
                }
            }
        }

        override fun getItemCount() = urls.size
    }
}