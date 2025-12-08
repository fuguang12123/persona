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

    /**
     * @class com.example.persona.data.remote.AuthInterceptor
     * @description OkHttp 认证拦截器：为所有请求添加 `Authorization` 头；仅当服务器明确返回 401 时清理会话并触发注销；网络异常（如断网/超时）不进行注销，以避免错误状态下误清理登录态。与 DataStore 的会话信息联动，上层通过 Hilt 注入单例复用。对应《最终作业.md》的工程规范与可观测性（错误码与日志），并作为从 Mock 到真实服务（C3）的关键安全机制。
     * @author Persona Team <persona@project.local>
     * @version v1.0.0
     * @since 2025-11-30
     * @see com.example.persona.di.NetworkModule
     * @关联功能 REQ-C3 架构演进-认证与会话管理
     */

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
