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

    private val BASE_URL: String
        get() {
            val port = "8080"
            // 检测是否在模拟器上运行
            val isEmulator = android.os.Build.FINGERPRINT.startsWith("" +
                    "generic")
                    || android.os.Build.FINGERPRINT.startsWith("unknown")
                    || android.os.Build.MODEL.contains("google_sdk")
                    || android.os.Build.MODEL.contains("Emulator")
                    || android.os.Build.MODEL.contains("Android SDK built for x86")
                    || android.os.Build.MANUFACTURER.contains("Genymotion")
                    || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                    || "google_sdk" == android.os.Build.PRODUCT
            
            // 模拟器用 10.0.2.2，真机用 localhost
            return if (isEmulator) {
                "http://10.0.2.2:$port/"
            } else {
                "http://localhost:$port/"
            }
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