package com.example.gamearchive

import androidx.annotation.Keep
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// --- Bangumi 条目收藏 ---
@Keep data class BangumiPagedCollection(
    val total: Int, val limit: Int, val offset: Int, val data: List<BangumiCollection>?
)

@Keep data class BangumiCollection(
    val subject_id: Int, val subject_type: Int, val rate: Int, val type: Int,
    val comment: String?, val tags: List<String>?, val ep_status: Int,
    val vol_status: Int, val updated_at: String?, val `private`: Boolean,
    val subject: BangumiSubject?
)

@Keep data class BangumiSubject(
    val id: Int, val name: String, val name_cn: String?, val type: Int,
    val summary: String?, val eps: Int?, val total_episodes: Int?,
    val rating: BangumiRating?, val images: BangumiImages?
)

@Keep data class BangumiRating(val score: Double?, val total: Int?)

@Keep data class BangumiImages(
    val large: String?, val common: String?, val medium: String?, val small: String?, val grid: String?
)

// --- 用户信息 ---
@Keep data class BangumiUser(
    val id: Int, val username: String, val nickname: String,
    val avatar: BangumiUserAvatar?, val sign: String?
)
@Keep data class BangumiUserAvatar(
    val large: String?, val medium: String?, val small: String?
)

// --- 接口 ---
interface BangumiService {
    @GET("bangumi/v0/users/{username}")
    suspend fun getUserInfo(
        @Path("username") username: String
    ): BangumiUser

    @GET("bangumi/v0/users/{username}/collections")
    suspend fun getUserCollections(
        @Path("username") username: String,
        @Query("subject_type") subjectType: Int = 2,  // 2=动画
        @Query("type") collectionType: Int? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): BangumiPagedCollection
}
