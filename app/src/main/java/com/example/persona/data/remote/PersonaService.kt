package com.example.persona.data.remote

import com.example.persona.data.model.Persona
import com.example.persona.data.model.PersonaRecommendationDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// 基础响应包装
data class Result<T>(val code: Int, val message: String, val data: T?) {
    fun isSuccess() = code == 200
}

data class GenerateRequest(val name: String)

interface PersonaService {
    // --- 广场与推荐 ---

    // [修改] 增加 page 和 size 参数支持分页
    @GET("personas/feed")
    suspend fun getFeed(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Result<List<Persona>>

    // 推荐接口通常不分页，或者有单独的分页逻辑
    @GET("personas/recommend")
    suspend fun getRecommend(
        @Header("X-User-Id") userId: Long?
    ): Result<List<PersonaRecommendationDto>>

    // --- 基础操作 ---

    @GET("personas/{id}")
    suspend fun getPersona(@Path("id") id: Long): Result<Persona>

    @POST("personas")
    suspend fun createPersona(@Body persona: Persona): Result<String>

    @PUT("personas/{id}")
    suspend fun updatePersona(@Path("id") id: Long, @Body persona: Persona): Result<String>

    @POST("ai/generate-persona")
    suspend fun generatePersonaProfile(@Body req: GenerateRequest): Result<String>

    // --- 关注相关接口 ---

    @POST("follows/{id}")
    suspend fun toggleFollow(@Path("id") id: Long): Result<Boolean>

    @GET("follows/status/{id}")
    suspend fun getFollowStatus(@Path("id") id: Long): Result<Boolean>

    @GET("follows/list")
    suspend fun getFollowedList(): Result<List<Persona>>
}