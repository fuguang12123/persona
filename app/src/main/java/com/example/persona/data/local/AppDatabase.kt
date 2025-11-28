package com.example.persona.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.persona.data.local.converter.PostTypeConverters
import com.example.persona.data.local.dao.ChatDao
import com.example.persona.data.local.dao.PersonaDao
import com.example.persona.data.local.dao.PostDao
import com.example.persona.data.local.dao.UserMemoryDao
import com.example.persona.data.local.entity.ChatMessageEntity
import com.example.persona.data.local.entity.PersonaEntity
import com.example.persona.data.local.entity.PostEntity
import com.example.persona.data.local.entity.UserMemoryEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        PersonaEntity::class,
        PostEntity::class,
        UserMemoryEntity::class // [New] 注册 UserMemory 表
    ],
    version = 5, // [Update] 升级版本号
    exportSchema = false
)
@TypeConverters(PostTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun personaDao(): PersonaDao
    abstract fun postDao(): PostDao
    abstract fun userMemoryDao(): UserMemoryDao // [New] 暴露 DAO
}