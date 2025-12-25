package com.example.gamearchive

import android.content.Intent
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

data class MarketGame(
    val id: Int,
    val name: String,
    val imgUrl: String,
    val finalPriceStr: String,
    val originalPriceStr: String?,
    val discount: Int,
    val reviewDesc: String? = null,
    val backupImgUrl: String? = null,
    val priceVal: Double = 0.0,
    val reviewScore: Int = -1
)

class MarketAdapter(private var items: List<MarketGame>) : RecyclerView.Adapter<MarketAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivGameIcon)
        val tvName: TextView = view.findViewById(R.id.tvGameName)
        val tvReview: TextView = view.findViewById(R.id.tvReviewScore)
        val tvFinal: TextView = view.findViewById(R.id.tvFinalPrice)
        val tvOriginal: TextView = view.findViewById(R.id.tvOriginalPrice)
        val tvDiscount: TextView = view.findViewById(R.id.tvDiscount)
        val cvDiscount: View = view.findViewById(R.id.cvDiscount)
    }

    // 更新列表数据
    fun updateData(newItems: List<MarketGame>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_game_market, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val game = items[position]
        holder.tvName.text = game.name

        // 1. 加载游戏封面 (包含缓存策略和备用图回退)
        val cacheKey = "market_${game.id}"
        holder.ivIcon.load(game.imgUrl) {
            crossfade(true)
            placeholder(R.drawable.placeholder_grey)
            error(R.drawable.placeholder_grey)
            memoryCacheKey(cacheKey)
            diskCacheKey(cacheKey)
            size(400, 220) // 限制解码尺寸以优化列表滑动性能
            listener(onError = { _, _ ->
                // 如果主图加载失败，尝试加载备用图
                if (game.backupImgUrl != null) {
                    holder.ivIcon.load(game.backupImgUrl) {
                        crossfade(false)
                        size(400, 220)
                        memoryCacheKey(cacheKey)
                        diskCacheKey(cacheKey)
                        placeholder(R.drawable.placeholder_grey)
                    }
                }
            })
        }

        // 2. 显示价格和折扣
        holder.tvFinal.text = game.finalPriceStr
        if (game.discount > 0) {
            holder.cvDiscount.visibility = View.VISIBLE
            holder.tvDiscount.text = "-${game.discount}%"
            holder.tvOriginal.visibility = View.VISIBLE
            holder.tvOriginal.text = game.originalPriceStr
            holder.tvOriginal.paintFlags = holder.tvOriginal.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.cvDiscount.visibility = View.GONE
            holder.tvOriginal.visibility = View.GONE
        }

        // 3. 显示好评率 (使用固定颜色值避免解析错误)
        if (game.reviewScore >= 0) {
            holder.tvReview.visibility = View.VISIBLE
            holder.tvReview.text = "${game.reviewScore}% 好评"

            val color = when {
                game.reviewScore >= 95 -> 0xFFE65100.toInt() // 好评如潮
                game.reviewScore >= 70 -> 0xFF1565C0.toInt() // 特别好评
                game.reviewScore >= 40 -> 0xFF616161.toInt() // 褒贬不一
                else -> 0xFFD32F2F.toInt()                   // 差评
            }
            holder.tvReview.setTextColor(color)

        } else {
            holder.tvReview.visibility = View.GONE
        }

        // 4. 点击跳转详情页
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, DetailActivity::class.java)
            intent.putExtra("APP_ID", game.id)
            intent.putExtra("APP_NAME", game.name)
            intent.putExtra("HEADER_URL", "https://cdn.cloudflare.steamstatic.com/steam/apps/${game.id}/header.jpg")
            intent.putExtra("APP_PRICE", game.finalPriceStr)
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount() = items.size
}