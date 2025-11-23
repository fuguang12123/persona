package com.example.persona.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.persona.data.local.converter.PostTypeConverters
import com.example.persona.data.local.dao.ChatDao
import com.example.persona.data.local.dao.PersonaDao
import com.example.persona.data.local.dao.PostDao
import com.example.persona.data.local.entity.ChatMessageEntity
import com.example.persona.data.local.entity.PersonaEntity
import com.example.persona.data.local.entity.PostEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        PersonaEntity::class,
        PostEntity::class // [New] 注册新表
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(PostTypeConverters::class) // [New] 注册转换器
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun personaDao(): PersonaDao
    abstract fun postDao(): PostDao // [New] 暴露 DAO
}