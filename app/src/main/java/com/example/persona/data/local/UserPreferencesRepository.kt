package com.example.persona.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// DataStore 扩展属性
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * @class com.example.persona.data.local.UserPreferencesRepository
     * @description 基于 DataStore 的用户偏好与会话持久化仓库，提供用户ID/Token/头像/昵称的 Flow 观察与更新方法，用于支持多账户登录、资料展示与云端认证拦截。在 MVVM 中作为本地 SSOT 提供者，UI 与网络层分别订阅其数据，保证一致性与解耦。对应《最终作业.md》的从 Mock 到真实服务（C3），并为 AuthInterceptor 提供认证信息来源。
     * @author Persona Team <persona@project.local>
     * @version v1.0.0
     * @since 2025-11-30
     * @see com.example.persona.data.remote.AuthInterceptor
     * @关联功能 REQ-C3 架构演进-多账户与数据隔离
     */
    // 键定义
    private val USER_ID_KEY = stringPreferencesKey("user_id")
    private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
    private val AVATAR_URL_KEY = stringPreferencesKey("avatar_url") // 头像
    private val USER_NAME_KEY = stringPreferencesKey("user_name")   // 昵称

    // Flow 数据流 (UI 通过观察这些属性来获取头像)
    val userId: Flow<String?> = context.dataStore.data.map { it[USER_ID_KEY] }
    val authToken: Flow<String?> = context.dataStore.data.map { it[AUTH_TOKEN_KEY] }
    val avatarUrl: Flow<String?> = context.dataStore.data.map { it[AVATAR_URL_KEY] }
    val userName: Flow<String?> = context.dataStore.data.map { it[USER_NAME_KEY] }

    /**
     * [New] 单独更新用户信息 (头像和昵称)
     * 专门用于 ChatViewModel 从后端同步最新数据后写入
     */
    suspend fun saveUserInfo(avatar: String?, name: String?) {
        context.dataStore.edit { prefs ->
            if (avatar != null) {
                prefs[AVATAR_URL_KEY] = avatar
                Log.d("UserPreferencesRepo", "Updating Avatar: $avatar")
            }
            if (name != null) {
                prefs[USER_NAME_KEY] = name
                Log.d("UserPreferencesRepo", "Updating Name: $name")
            }
        }
    }

    /**
     * 完整保存会话 (包含头像和昵称)
     */
    suspend fun saveUserSession(id: String, token: String, avatar: String? = null, name: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[USER_ID_KEY] = id
            prefs[AUTH_TOKEN_KEY] = token
            if (avatar != null) prefs[AVATAR_URL_KEY] = avatar
            if (name != null) prefs[USER_NAME_KEY] = name
            Log.d("UserPreferencesRepo", "Session Saved: $id, $avatar")
        }
    }

    /**
     * 兼容旧代码的方法
     */
    suspend fun saveAuthData(token: String, userId: String) {
        // 转发给新方法
        saveUserSession(id = userId, token = token)
    }

    /**
     * 清除会话
     */
    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }

    /**
     * 兼容旧代码
     */
    suspend fun clearAuth() {
        clearSession()
    }

    // 简单的更新头像方法
    suspend fun updateAvatar(url: String) {
        context.dataStore.edit { prefs ->
            prefs[AVATAR_URL_KEY] = url
        }
    }
}
