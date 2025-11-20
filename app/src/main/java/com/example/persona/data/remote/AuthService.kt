package com.example.persona.data.remote

import com.example.persona.data.model.ApiResponse
import com.example.persona.data.model.AuthData
import com.example.persona.data.model.LoginRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST


interface AuthService {
    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthData>>

    @POST("/auth/register")
    suspend fun register(@Body request: LoginRequest): Response<ApiResponse<AuthData>>
}