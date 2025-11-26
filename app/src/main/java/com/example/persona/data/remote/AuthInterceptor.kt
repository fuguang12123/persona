package com.example.persona.data.remote

import android.util.Log
import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.manager.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val userPrefs: UserPreferencesRepository,
    private val sessionManager: SessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            userPrefs.authToken.first()
        }

        val requestBuilder = chain.request().newBuilder()
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        try {
            val response = chain.proceed(requestBuilder.build())

            // 🚨 核心逻辑修正：只有服务器明确返回 401 时才视为 Token 过期
            if (response.code == 401) {
                Log.w("AuthInterceptor", "401 Unauthorized detected. Triggering logout.")
                runBlocking {
                    userPrefs.clearAuth()
                    sessionManager.triggerLogout()
                }
            }
            return response

        } catch (e: IOException) {
            // ⚠️ 网络连接失败 (如断网、超时、DNS 失败)
            // 绝对不要在这里触发 Logout！直接抛出异常让 UI 层提示"网络错误"
            Log.e("AuthInterceptor", "Network error: ${e.message}")
            throw e
        }
    }
}