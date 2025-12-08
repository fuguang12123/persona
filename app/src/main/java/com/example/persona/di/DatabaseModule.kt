package com.example.persona.di

import android.content.Context
import androidx.room.Room
import com.example.persona.data.local.AppDatabase
import com.example.persona.data.local.dao.ChatDao
import com.example.persona.data.local.dao.PersonaDao
import com.example.persona.data.local.dao.PostDao
import com.example.persona.data.local.dao.UserMemoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * @class com.example.persona.di.DatabaseModule
     * @description Hilt 数据库模块：提供 Room 数据库与各 DAO 的单例注入。通过 `fallbackToDestructiveMigration` 支持开发阶段快速迭代；生产环境应改为 Migration 脚本。该模块为 UI 的 SSOT 缓存与事务一致性提供基础设施，对应《最终作业.md》的工程质量与架构设计要求。
     * @author Persona Team <persona@project.local>
     * @version v1.0.0
     * @since 2025-11-30
     * @see com.example.persona.data.local.AppDatabase
     * @关联功能 REQ-C3 架构演进-数据层注入
     */

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "persona_database"
        )
            .fallbackToDestructiveMigration() // 开发阶段允许破坏性迁移
            .build()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    @Singleton
    fun providePersonaDao(database: AppDatabase): PersonaDao {
        return database.personaDao()
    }

    @Provides
    @Singleton
    fun providePostDao(database: AppDatabase): PostDao {
        return database.postDao()
    }

    // [New] 注入 UserMemoryDao
    @Provides
    @Singleton
    fun provideUserMemoryDao(database: AppDatabase): UserMemoryDao {
        return database.userMemoryDao()
    }
}
