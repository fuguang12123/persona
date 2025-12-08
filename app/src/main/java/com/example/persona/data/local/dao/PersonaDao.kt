package com.example.persona.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.persona.data.local.entity.PersonaEntity
import kotlinx.coroutines.flow.Flow

/**
 * @class com.example.persona.data.local.dao.PersonaDao
 * @description Persona 实体的 Room DAO：提供广场列表的 Flow 观察、单个详情查询、批量插入与清空。与 `PersonaRepository` 协作，承担 UI 的 SSOT 缓存源；创建/更新后写入本地，支持发现与详情页的快速加载。对应《最终作业.md》的 Persona 创作与社交广场需求路径。
 * @author Persona Team <persona@project.local>
 * @version v1.0.0
 * @since 2025-11-30
 * @see com.example.persona.data.repository.PersonaRepository
 * @关联功能 REQ-B1 Persona创作；REQ-B3 社交广场
 */
@Dao
interface PersonaDao {
    // 获取广场列表 (Flow 实时更新)
    @Query("SELECT * FROM personas WHERE is_public = 1 ORDER BY id DESC")
    fun getAllPersonas(): Flow<List<PersonaEntity>>

    // 🔥 [新增] 根据 ID 获取单个 Persona 详情 (用于端侧 AI 读取人设)
    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun getPersona(id: Long): PersonaEntity?

    // 批量插入/更新 (服务器数据回来后调用)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(personas: List<PersonaEntity>)

    // 清空表 (下拉刷新时，如果想完全重置可调用)
    @Query("DELETE FROM personas")
    suspend fun clearAll()
}
