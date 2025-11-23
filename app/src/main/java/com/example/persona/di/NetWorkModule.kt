package com.example.persona.di

import android.os.Build
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

    // 🔴 重点修改：不再是写死的常量，而是根据设备类型动态获取
    private val BASE_URL: String
        get() {
            // 端口号：如果你之前 adb reverse 用的 9000，这里要改成 9000
            val port = "8080"

            return if (isEmulator) {
                // 模拟器环境：访问宿主机的特殊 IP
                "http://10.0.2.2:$port/"
            } else {
                // 真机环境 (配合 adb reverse)：访问手机本地的回环地址
                "http://localhost:$port/"
            }
        }

    /**
     * 判断当前运行设备是否为模拟器的辅助属性
     */
    private val isEmulator: Boolean
        get() = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("emulator")
                || Build.MANUFACTURER.contains("Genymotion")

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // 解决真机有时候 socket 关闭过慢的问题
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            // 这里会自动调用上面的 BASE_URL 逻辑
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthService(retrofit: Retrofit): AuthService {
        return retrofit.create(AuthService::class.java)
    }

    @Provides
    @Singleton
    fun providePersonaService(retrofit: Retrofit): PersonaService {
        return retrofit.create(PersonaService::class.java)
    }

    @Provides
    @Singleton
    fun provideChatService(retrofit: Retrofit): ChatService {
        return retrofit.create(ChatService::class.java)
    }

    @Provides
    @Singleton
    fun providePostService(retrofit: Retrofit): PostService {
        return retrofit.create(PostService::class.java)
    }
}