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
    /**
     * @class com.example.persona.data.repository.PersonaRepository
     * @description Persona 领域仓库，负责广场信息流、本地缓存转换、推荐与关注相关逻辑，以及创建/更新 Persona 的数据通路。通过 `PersonaService` 获取远端数据并写入 Room，实现 UI 的单一数据源与快速回显；创建成功后主动刷新第一页，提升用户反馈体验。与《最终作业.md》对应基础与进阶：Persona 创作（B1）、社交广场（B2/B3）、智能推荐（C5）、从 Mock 到真实服务（C3）。
     * @author Persona Team <persona@project.local>
     * @version v1.0.0
     * @since 2025-11-30
     * @see com.example.persona.data.remote.PersonaService
     * @关联功能 REQ-B1/B2/B3；REQ-C5 推荐；REQ-C3 架构演进
     */

    // 获取本地缓存流
    fun getFeedStream(): Flow<List<Persona>> {
        return personaDao.getAllPersonas().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * 功能: 拉取广场数据（分页），并落库以供 UI 复用；支持推荐模式与普通模式。
     * 实现逻辑: 根据 type 切换接口；成功则写入 Room。
     * @return Boolean - 是否可能还有下一页（以 size 判断）
     * 关联功能: REQ-B3 社交广场-浏览与互动
     */
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
        } catch (   e: Exception) { false }
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
    /**
     * 功能: 创建 Persona 成功后自动拉取第一页数据，实现列表的即时更新（无手动刷新）。
     * 实现逻辑: POST 创建 -> 成功后调用 `fetchFeed(1,20)`。
     * 返回值: Boolean - 创建结果
     * 关联功能: REQ-B1 Persona创作；REQ-C3 架构演进-数据源联动
     */
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
