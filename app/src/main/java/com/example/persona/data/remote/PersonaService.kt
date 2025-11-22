package com.example.persona.data.remote

import com.example.persona.data.model.BaseResponse
import com.example.persona.data.model.Persona
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class AiGenRequest(@SerializedName("name") val name: String)

interface PersonaService {
    // ✅ 修改 1: 广场流，直接返回 BaseResponse
    @GET("/personas/feed")
    suspend fun getFeed(): BaseResponse<List<Persona>>

    // ✅ 修改 2: 获取单个分身
    @GET("/personas/{id}")
    suspend fun getPersona(@Path("id") id: Long): BaseResponse<Persona>

    // ✅ 修改 3: 创建分身
    @POST("/personas")
    suspend fun createPersona(@Body persona: Persona): BaseResponse<String>

    // ✅ 修改 4: AI 生成
    @POST("/ai/generate-persona")
    suspend fun generatePersonaDescription(@Body req: AiGenRequest): BaseResponse<String>
}