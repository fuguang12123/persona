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
                    // 保存到数据库，OnConflictStrategy.REPLACE 保证了如果 ID 相同则更新
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

    // ----------------------------------------------------------------
    // 🔥 [核心修改] 创建成功后，自动拉取第一页数据
    // ----------------------------------------------------------------
    suspend fun createPersona(persona: Persona): Boolean {
        return try {
            val response = api.createPersona(persona)
            if (response.isSuccess()) {
                // 修改点：创建成功后，后台静默拉取最新的第一页数据并写入本地数据库。
                // 这样当用户返回列表页时，SocialFeedViewModel 监听的 Flow 会自动更新，
                // 显示出刚刚创建的那个智能体，无需手动刷新。
                fetchFeed(page = 1, size = 20, type = "all")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
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

    private fun PersonaEntity.toDomainModel(): Persona {
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
            tagsList = derivedTags
        )
    }
}