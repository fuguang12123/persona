package com.example.persona.data.repository

import android.util.Log
import com.example.persona.data.local.dao.PersonaDao
import com.example.persona.data.local.entity.PersonaEntity
import com.example.persona.data.model.Persona
import com.example.persona.data.remote.PersonaService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonaRepository @Inject constructor(
    private val personaDao: PersonaDao,
    private val api: PersonaService
) {

    // 1. 核心流：SSOT 模式
    // 数据库一变，这个 Flow 就会发射最新的 UI 数据列表
    fun getFeedStream(): Flow<List<Persona>> {
        return personaDao.getAllPersonas().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    // 2. 刷新逻辑：静默更新
    // 网络请求 -> 拿到数据 -> 存入数据库 -> 触发上面的 Flow 更新
    suspend fun refreshFeed() {
        try {
            val response = api.getFeed()
            if (response.isSuccess() && response.data != null) {
                val entities = response.data.map { it.toEntity() }
                personaDao.insertAll(entities)
            } else {
                Log.e("PersonaRepo", "Refresh error: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e("PersonaRepo", "Refresh failed: ${e.message}")
            // 失败了也不要紧，UI 继续显示数据库里的旧缓存
        }
    }

    // 3. 获取单个详情 (顺便更新缓存)
    suspend fun getPersona(id: Long): Persona? {
        try {
            val response = api.getPersona(id)
            if (response.isSuccess() && response.data != null) {
                personaDao.insertAll(listOf(response.data.toEntity()))
                return response.data
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // 4. 创建分身 (透传)
    suspend fun createPersona(persona: Persona): Boolean {
        return try {
            val response = api.createPersona(persona)
            response.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    // 5. AI 生成 (透传)
    suspend fun generateDescription(name: String): String {
        return try {
            val req = com.example.persona.data.remote.AiGenRequest(name)
            val response = api.generatePersonaDescription(req)
            if (response.isSuccess()) response.data ?: "" else ""
        } catch (e: Exception) {
            "AI 生成失败，请重试"
        }
    }

    // --- Mappers ---
    private fun Persona.toEntity() = PersonaEntity(
        id = this.id,
        userId = this.userId ?: 0L,
        name = this.name ?: "Unknown",
        avatarUrl = this.avatarUrl,
        description = this.description,
        personalityTags = this.personalityTags,
        isPublic = this.isPublic ?: true
    )

    private fun PersonaEntity.toDomainModel() = Persona(
        id = this.id,
        userId = this.userId,
        name = this.name,
        avatarUrl = this.avatarUrl,
        description = this.description,
        personalityTags = this.personalityTags,
        isPublic = this.isPublic
    )
}