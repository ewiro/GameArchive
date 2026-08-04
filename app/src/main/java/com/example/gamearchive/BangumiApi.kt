package com.example.gamearchive

import androidx.annotation.Keep
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
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
    val rating: Any? = null, val images: BangumiImages?, val date: String?,
    val tags: List<BangumiTag>? = null
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
    @POST("bangumi/v0/search/subjects")
    suspend fun searchSubjects(
        @Body payload: BangumiSubjectSearchRequest,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): BangumiPagedSubjects

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

    @GET("bangumi/v0/subjects/{subject_id}/characters")
    suspend fun getSubjectCharacters(
        @Path("subject_id") subjectId: Int
    ): List<BangumiRelatedCharacter>

    @GET("bangumi/v0/episodes")
    suspend fun getSubjectEpisodes(
        @Query("subject_id") subjectId: Int,
        @Query("type") episodeType: Int = 0,
        @Query("limit") limit: Int = 1,
        @Query("offset") offset: Int = 0
    ): BangumiPagedEpisodes
}

@Keep data class BangumiSubjectSearchFilter(
    val type: List<Int>
)

@Keep data class BangumiSubjectSearchRequest(
    val keyword: String,
    val sort: String = "match",
    val filter: BangumiSubjectSearchFilter
)

@Keep data class BangumiPagedSubjects(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val data: List<BangumiSubjectDetail>? = null
)

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
@Keep data class BangumiRelatedCharacter(
    val id: Int? = null,
    val name: String? = null,
    val relation: String? = null,
    val actors: List<BangumiPerson>? = null
)

// --- OAuth 接口（通过公共代理转发到 bgm.tv）---
interface BangumiOAuthService {
    @FormUrlEncoded
    @POST("bangumi-oauth/access_token")
    suspend fun getToken(
        @Field("grant_type") grantType: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String? = null,
        @Field("refresh_token") refreshToken: String? = null,
        @Field("redirect_uri") redirectUri: String
    ): BangumiOAuthToken
}

// --- 用户收藏编辑（需 Bearer Token）---
@Keep data class BangumiMyCollection(
    val subject_id: Int? = null,
    val subject_type: Int? = null,
    val rate: Int? = null,
    val type: Int? = null,
    val comment: String? = null,
    val tags: List<String>? = null,
    val ep_status: Int? = null,
    val vol_status: Int? = null,
    val updated_at: String? = null,
    val `private`: Boolean? = null,
    val subject: BangumiSubject? = null
)

@Keep data class BangumiCollectionUpdate(
    val type: Int,
    val rate: Int,
    val comment: String,
    val tags: List<String>,
    val `private`: Boolean
)

@Keep data class BangumiLegacyCollectionResult(
    val code: Int? = null,
    val error: String? = null
)

@Keep data class BangumiEpisode(
    val id: Int,
    val type: Int,
    val name: String? = null,
    val name_cn: String? = null,
    val sort: Double? = null,
    val ep: Double? = null
)

@Keep data class BangumiPagedEpisodes(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val data: List<BangumiEpisode>? = null
)

@Keep data class BangumiUserEpisodeCollection(
    val episode: BangumiEpisode,
    val type: Int,
    val updated_at: Long? = null
)

@Keep data class BangumiPagedEpisodeCollection(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val data: List<BangumiUserEpisodeCollection>? = null
)

@Keep data class BangumiEpisodeCollectionUpdate(
    val episode_id: List<Int>,
    val type: Int
)

interface BangumiCollectionService {
    @GET("bangumi/v0/me")
    suspend fun getCurrentUser(): BangumiUser

    @GET("bangumi/v0/users/{username}/collections/{subject_id}")
    suspend fun getMyCollection(
        @Path("username") username: String,
        @Path("subject_id") subjectId: Int
    ): BangumiMyCollection

    @FormUrlEncoded
    @POST("bangumi/collection/{subject_id}/update")
    suspend fun updateCollectionLegacy(
        @Path("subject_id") subjectId: Int,
        @Field("app_id") appId: String,
        @Field("status") status: String,
        @Field("tags") tags: String,
        @Field("comment") comment: String,
        @Field("rating") rating: Int,
        @Field("privacy") privacy: Int
    ): Response<BangumiLegacyCollectionResult>

    @POST("bangumi/v0/users/-/collections/{subject_id}")
    suspend fun updateCollection(
        @Path("subject_id") subjectId: Int,
        @Body payload: BangumiCollectionUpdate
    ): Response<Unit>

    @GET("bangumi/v0/users/-/collections/{subject_id}/episodes")
    suspend fun getEpisodeCollections(
        @Path("subject_id") subjectId: Int,
        @Query("limit") limit: Int = 1000,
        @Query("episode_type") episodeType: Int = 0
    ): BangumiPagedEpisodeCollection

    @PATCH("bangumi/v0/users/-/collections/{subject_id}/episodes")
    suspend fun updateEpisodeCollections(
        @Path("subject_id") subjectId: Int,
        @Body payload: BangumiEpisodeCollectionUpdate
    ): Response<Unit>
}
