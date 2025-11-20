package com.example.persona.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.persona.data.local.entity.PersonaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {
    // 获取广场列表 (Flow 实时更新)
    @Query("SELECT * FROM personas WHERE is_public = 1 ORDER BY id DESC")
    fun getAllPersonas(): Flow<List<PersonaEntity>>

    // 批量插入/更新 (服务器数据回来后调用)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(personas: List<PersonaEntity>)

    // 清空表 (下拉刷新时，如果想完全重置可调用)
    @Query("DELETE FROM personas")
    suspend fun clearAll()
}