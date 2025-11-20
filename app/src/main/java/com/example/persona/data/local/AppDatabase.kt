package com.example.persona.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.persona.data.local.dao.ChatDao
import com.example.persona.data.local.dao.PersonaDao
import com.example.persona.data.local.entity.ChatMessageEntity
import com.example.persona.data.local.entity.PersonaEntity

@Database(
    entities = [ChatMessageEntity::class, PersonaEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // 这里稍后会放 DAO 的抽象方法
    abstract fun chatDao(): ChatDao
    abstract fun personaDao(): PersonaDao
}