package com.example.persona.data.remote

import com.example.persona.data.model.ApiResponse
import com.example.persona.data.model.Persona
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class AiGenRequest(    @SerializedName("name") val name: String)
interface PersonaService {
    @GET("/personas/feed")
    suspend fun getFeed(): Response<ApiResponse<List<Persona>>>
    // 在 interface PersonaService 中添加：
    @GET("/personas/{id}")
    suspend fun getPersona(@Path("id") id: Long): Response<ApiResponse<Persona>>
    @POST("/personas")
    suspend fun createPersona(@Body persona: Persona): Response<ApiResponse<String>>
    @POST("/ai/generate-persona")
    suspend fun generatePersonaDescription(@Body req: AiGenRequest): Response<ApiResponse<String>>

}
