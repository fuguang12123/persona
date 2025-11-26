package com.example.persona.data.repository

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

    fun getFeedStream(): Flow<List<Persona>> {
        return personaDao.getAllPersonas().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun refreshFeed() {
        try {
            val response = api.getFeed()
            if (response.isSuccess() && response.data != null) {
                val entities = response.data.map { it.toEntity() }
                personaDao.insertAll(entities)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
        // 如果网络失败，尝试从数据库读取缓存（可选优化，暂不实现）
        return null
    }

    suspend fun createPersona(persona: Persona): Boolean {
        return try {
            val response = api.createPersona(persona)
            response.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    // [New] 更新智能体
    suspend fun updatePersona(id: Long, persona: Persona): Boolean {
        return try {
            val response = api.updatePersona(id, persona)
            if (response.isSuccess()) {
                // 更新成功后，同步更新本地数据库缓存，让 UI 即时刷新
                val updatedEntity = persona.copy(id = id).toEntity()
                personaDao.insertAll(listOf(updatedEntity))
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun generateDescription(name: String): String {
        return try {
            val req = com.example.persona.data.remote.AiGenRequest(name)
            val response = api.generatePersonaDescription(req)
            if (response.isSuccess()) response.data ?: "" else ""
        } catch (e: Exception) {
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