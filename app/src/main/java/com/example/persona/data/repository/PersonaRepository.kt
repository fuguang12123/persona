package com.example.persona.data.repository

import com.example.persona.data.local.UserPreferencesRepository
import com.example.persona.data.local.dao.PersonaDao
import com.example.persona.data.local.entity.PersonaEntity
import com.example.persona.data.model.Persona
import com.example.persona.data.remote.GenerateRequest
import com.example.persona.data.remote.PersonaService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonaRepository @Inject constructor(
    private val personaDao: PersonaDao,
    private val api: PersonaService,
    private val userPrefs: UserPreferencesRepository
) {

    // 获取本地缓存流
    fun getFeedStream(): Flow<List<Persona>> {
        return personaDao.getAllPersonas().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun fetchFeed(page: Int, size: Int, type: String = "all"): Boolean {
        return try {
            if (type == "recommend") {
                val currentUserId = getCurrentUserId()
                val headerId = if (currentUserId != null && currentUserId > 0) currentUserId else null
                val response = api.getRecommend(headerId)

                if (response.isSuccess() && response.data != null) {
                    false
                } else {
                    false
                }
            } else {
                val response = api.getFeed(page, size)
                if (response.isSuccess() && response.data != null) {
                    val list = response.data
                    val entities = list.map { it.toEntity() }
                    personaDao.insertAll(entities)
                    list.size >= size
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getRecommendList(): List<Persona> {
        return try {
            val currentUserId = getCurrentUserId()
            val headerId = if (currentUserId != null && currentUserId > 0) currentUserId else null
            val response = api.getRecommend(headerId)
            if (response.isSuccess() && response.data != null) {
                response.data.map { dto ->
                    Persona(
                        id = dto.id,
                        name = dto.name,
                        avatarUrl = dto.avatarUrl,
                        description = dto.reason ?: "AI 推荐",
                        matchScore = dto.matchScore,
                        reason = dto.reason,
                        tagsList = dto.tags ?: emptyList(),
                        isPublic = true,
                        userId = 0L
                    )
                }
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getCurrentUserId(): Long? {
        val idStr = userPrefs.userId.first()
        return idStr?.toLongOrNull()
    }

    // ... 其他方法保持不变 ...

    suspend fun toggleFollow(id: Long): Boolean {
        return try {
            val res = api.toggleFollow(id)
            res.isSuccess() && res.data == true
        } catch (e: Exception) { false }
    }

    suspend fun getFollowStatus(id: Long): Boolean {
        return try {
            val res = api.getFollowStatus(id)
            res.isSuccess() && res.data == true
        } catch (e: Exception) { false }
    }

    suspend fun getFollowedPersonas(): List<Persona> {
        return try {
            val res = api.getFollowedList()
            if (res.isSuccess()) res.data ?: emptyList() else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getPersona(id: Long): Persona? {
        try {
            val response = api.getPersona(id)
            if (response.isSuccess() && response.data != null) {
                personaDao.insertAll(listOf(response.data.toEntity()))
                return response.data
            }
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    suspend fun createPersona(persona: Persona): Boolean {
        return try {
            val response = api.createPersona(persona)
            response.isSuccess()
        } catch (e: Exception) { false }
    }

    suspend fun updatePersona(id: Long, persona: Persona): Boolean {
        return try {
            val response = api.updatePersona(id, persona)
            if (response.isSuccess()) {
                val updatedEntity = persona.copy(id = id).toEntity()
                personaDao.insertAll(listOf(updatedEntity))
                true
            } else { false }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun generateDescription(name: String): String {
        return try {
            val req = GenerateRequest(name = name)
            val response = api.generatePersonaProfile(req)
            if (response.isSuccess()) response.data ?: "" else ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun Persona.toEntity() = PersonaEntity(
        id = this.id,
        userId = this.userId ?: 0L,
        name = this.name,
        avatarUrl = this.avatarUrl,
        description = this.description,
        personalityTags = this.personalityTags,
        isPublic = this.isPublic ?: true
    )

    // [核心修改] 这里增加了 tagsList 的解析逻辑
    private fun PersonaEntity.toDomainModel(): Persona {
        // 尝试解析标签字符串：移除 JSON 符号，按逗号分割
        val derivedTags = if (!this.personalityTags.isNullOrEmpty()) {
            this.personalityTags
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        return Persona(
            id = this.id,
            userId = this.userId,
            name = this.name,
            avatarUrl = this.avatarUrl,
            description = this.description,
            personalityTags = this.personalityTags,
            isPublic = this.isPublic,
            tagsList = derivedTags // 将解析后的列表赋值给领域模型
        )
    }
}