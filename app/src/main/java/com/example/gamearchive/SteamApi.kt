package com.example.gamearchive

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// --- 1. 玩家个人资料 ---
@Keep data class PlayerSummaryResponse(val response: PlayerSummaryList)
@Keep data class PlayerSummaryList(val players: List<PlayerInfo>)
@Keep data class PlayerInfo(
    val steamid: String, val personaname: String, val avatarfull: String,
    val personastate: Int, val gameextrainfo: String?
)

// --- 2. 游戏库存 ---
@Keep data class SteamResponse(val response: GameListResponse)
@Keep data class GameListResponse(val game_count: Int, val games: List<GameInfo>)
@Keep data class GameInfo(
    val appid: Int, val name: String, val playtime_forever: Int,
    val img_icon_url: String, val playtime_2weeks: Int? = 0
)

// --- 3. 商店详情 ---
@Keep data class StoreAppDetails(val success: Boolean, val data: StoreGameData?)
@Keep data class StoreGameData(
    val price_overview: PriceOverview?, val short_description: String?,
    val detailed_description: String?, val header_image: String?,
    val screenshots: List<Screenshot>?, val movies: List<SteamMovie>?,
    val release_date: ReleaseDate?, val developers: List<String>?, val publishers: List<String>?,
    val name: String? // 中文名
)

@Keep data class PriceOverview(
    val currency: String?, val initial: Int?, val final: Int?,
    val discount_percent: Int?, val final_formatted: String?, val initial_formatted: String?
)

@Keep data class Screenshot(val id: Int?, val path_thumbnail: String?, val path_full: String?)
@Keep data class SteamMovie(
    val id: Int?, val name: String?, val thumbnail: String?,
    @SerializedName("mp4") val mp4: MovieQuality?,
    @SerializedName("webm") val webm: MovieQuality?
)
@Keep data class MovieQuality(
    @SerializedName("max") val max: String?,
    @SerializedName("480") val p480: String?
)
@Keep data class ReleaseDate(val date: String?)

// --- 4. 评价数据 (关键更新) ---
@Keep data class ReviewResponse(
    val query_summary: ReviewSummary?,
    val reviews: List<SteamReview>?
)

@Keep data class ReviewSummary(
    val review_score_desc: String?, val total_positive: Int,
    val total_reviews: Int, val total_positive_reviews: Int?, val total_reviews_last_30_days: Int?
)

@Keep data class SteamReview(
    val recommendationid: String,
    val author: ReviewAuthor,
    val review: String,
    val voted_up: Boolean,
    val timestamp_created: Long,
    val votes_up: Int
)

@Keep data class ReviewAuthor(
    val steamid: String,
    val num_games_owned: Int,
    val playtime_forever: Int
)

// --- 5. Steam等级 ---
@Keep data class SteamLevelResponse(val response: SteamLevelData)
@Keep data class SteamLevelData(val player_level: Int?)

// --- 接口定义 ---
interface SteamApiService {
    @GET("IPlayerService/GetOwnedGames/v0001/")
    suspend fun getOwnedGames(@Query("key") k: String, @Query("steamid") s: String, @Query("format") f: String = "json", @Query("include_appinfo") i: Boolean = true): SteamResponse

    @GET("ISteamUser/GetPlayerSummaries/v0002/")
    suspend fun getPlayerSummaries(@Query("key") k: String, @Query("steamids") s: String): PlayerSummaryResponse

    @GET("IPlayerService/GetSteamLevel/v1/")
    suspend fun getSteamLevel(@Query("key") k: String, @Query("steamid") s: String): SteamLevelResponse

    @GET("api/appdetails/")
    suspend fun getGamePrices(@Query("appids") ids: String, @Query("filters") f: String = "price_overview", @Query("l") l: String = "schinese", @Query("cc") cc: String = "cn"): Map<String, StoreAppDetails>

    @GET("api/appdetails/")
    suspend fun getGameDetails(@Query("appids") id: Int, @Query("l") l: String = "schinese", @Query("cc") cc: String = "cn"): Map<String, StoreAppDetails>

    // reviews 接口
    @GET("appreviews/{appid}")
    suspend fun getGameReviews(@Path("appid") id: Int, @Query("json") j: Int = 1, @Query("language") l: String = "schinese",@Query("num_per_page") count: Int = 100): ReviewResponse
}