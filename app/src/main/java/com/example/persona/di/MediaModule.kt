package com.example.persona.di

import android.content.Context
import com.example.persona.utils.AudioPlayerManager
import com.example.persona.utils.AudioRecorderManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideAudioRecorderManager(@ApplicationContext context: Context): AudioRecorderManager {
        return AudioRecorderManager(context)
    }

    @Provides
    @Singleton
    fun provideAudioPlayerManager(@ApplicationContext context: Context): AudioPlayerManager {
        return AudioPlayerManager(context)
    }
}