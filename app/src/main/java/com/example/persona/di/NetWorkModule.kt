package com.example.persona.di

import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.manager.SessionManager
import com.example.persona.data.remote.AuthInterceptor
import com.example.persona.data.remote.AuthService
import com.example.persona.data.remote.ChatService
import com.example.persona.data.remote.PersonaService
import com.example.persona.data.remote.PostService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * @class com.example.persona.di.NetworkModule
     * @description Hilt 单例网络模块，提供 `SessionManager`、`AuthInterceptor`、`OkHttpClient`、`Retrofit` 及各服务接口实例。通过拦截器统一处理认证与日志，设置合理的超时与重试策略，保障在弱网环境下的鲁棒性。该模块是 Clean Architecture 中的数据源注入点，上层通过 Repository 使用，避免直接依赖具体实现。对应《最终作业.md》的工程质量与架构设计要求。
     * @author Persona Team <persona@project.local>
     * @version v1.0.0
     * @since 2025-11-30
     * @see com.example.persona.data.remote.AuthInterceptor
     * @关联功能 REQ-C3 架构演进-网络层注入
     */

    private val BASE_URL: String
        get() {
            val port = "8080"


            return "http://111.228.53.151:$port/"

        }


    @Provides
    @Singleton
    fun provideSessionManager(): SessionManager {
        return SessionManager()
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        userPrefs: UserPreferencesRepository,
        sessionManager: SessionManager // [New] 注入
    ): AuthInterceptor {
        return AuthInterceptor(userPrefs, sessionManager)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthService(retrofit: Retrofit): AuthService = retrofit.create(AuthService::class.java)

    @Provides
    @Singleton
    fun providePersonaService(retrofit: Retrofit): PersonaService = retrofit.create(PersonaService::class.java)

    @Provides
    @Singleton
    fun provideChatService(retrofit: Retrofit): ChatService = retrofit.create(ChatService::class.java)

    @Provides
    @Singleton
    fun providePostService(retrofit: Retrofit): PostService = retrofit.create(PostService::class.java)
}
