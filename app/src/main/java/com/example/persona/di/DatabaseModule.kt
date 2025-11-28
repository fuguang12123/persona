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