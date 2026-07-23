package com.example.gamearchive

import androidx.annotation.Keep
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Path
import retrofit2.http.Query

// --- OAuth 令牌 ---
@Keep data class BangumiOAuthToken(
    val access_token: String, val expires_in: Int, val token_type: String,
    val scope: String?, val refresh_token: String, val user_id: Int
)
@Keep data class BangumiOAuthError(
    val error: String?, val error_description: String?
)

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
    val rating: Any? = null, val images: BangumiImages?, val date: String?
)

@Keep data class BangumiRating(val score: Any? = null, val total: Int? = null, val count: Any? = null, val rank: Int? = null)

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

    @GET("bangumi/v0/subjects/{subject_id}")
    suspend fun getSubject(
        @Path("subject_id") subjectId: Int
    ): BangumiSubjectDetail

    @GET("bangumi/v0/subjects/{subject_id}/persons")
    suspend fun getSubjectPersons(
        @Path("subject_id") subjectId: Int
    ): List<BangumiPerson>
}

/** 单条目详情（含完整 rating），只取需要的字段 */
@Keep data class BangumiSubjectDetail(
    val id: Int? = null,
    val name: String? = null,
    val name_cn: String? = null,
    val type: Int? = null,
    val summary: String? = null,
    val nsfw: Boolean? = null,
    val date: String? = null,
    val eps: Int? = null,
    val total_episodes: Int? = null,
    val rating: Any? = null,
    val images: BangumiImages? = null,
    val collection: Any? = null,
    val tags: List<BangumiTag>? = null,
    val infobox: List<BangumiInfoboxItem>? = null
)
@Keep data class BangumiTag(val name: String, val count: Int)
@Keep data class BangumiInfoboxItem(val key: String, val value: Any?)
@Keep data class BangumiPerson(
    val id: Int? = null,
    val name: String? = null,
    val relation: String? = null,
    val type: Int? = null,
    val career: List<String>? = null,
    val images: BangumiImages? = null
)

// --- OAuth 接口（直连 bgm.tv）---
interface BangumiOAuthService {
    @FormUrlEncoded
    @POST("oauth/access_token")
    suspend fun getToken(
        @Field("grant_type") grantType: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String? = null,
        @Field("refresh_token") refreshToken: String? = null,
        @Field("redirect_uri") redirectUri: String
    ): BangumiOAuthToken
}
