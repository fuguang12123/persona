package com.example.persona.di

import android.content.Context
import androidx.room.Room
import com.example.persona.data.local.AppDatabase
import com.example.persona.data.local.dao.ChatDao
import com.example.persona.data.local.dao.PersonaDao
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
            "persona_database" // 数据库文件名
        )
            // 开发阶段神技：如果改了表结构，直接清空重建，防止崩溃
            // 上线前记得去掉或改为标准的 Migration
            .fallbackToDestructiveMigration()
            .build()
    }

    // 提供 ChatDao 给 ChatRepository 使用
    @Provides
    @Singleton
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    // 提供 PersonaDao (虽然现在还没用，但既然定义了就先提供上)
    @Provides
    @Singleton
    fun providePersonaDao(database: AppDatabase): PersonaDao {
        return database.personaDao()
    }
}