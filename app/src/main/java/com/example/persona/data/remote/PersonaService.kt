package com.example.persona.data.remote

import com.example.persona.data.model.BaseResponse
import com.example.persona.data.model.Persona
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

data class AiGenRequest(@SerializedName("name") val name: String)

interface PersonaService {
    @GET("/personas/feed")
    suspend fun getFeed(): BaseResponse<List<Persona>>

    @GET("/personas/{id}")
    suspend fun getPersona(@Path("id") id: Long): BaseResponse<Persona>

    @POST("/personas")
    suspend fun createPersona(@Body persona: Persona): BaseResponse<String>

    // [New] 添加更新接口
    @PUT("/personas/{id}")
    suspend fun updatePersona(
        @Path("id") id: Long,
        @Body persona: Persona
    ): BaseResponse<String>

    @POST("/ai/generate-persona")
    suspend fun generatePersonaDescription(@Body req: AiGenRequest): BaseResponse<String>
}