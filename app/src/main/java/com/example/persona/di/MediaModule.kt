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
    /**
     * @class com.example.persona.di.MediaModule
     * @description 音频组件的 Hilt 注入模块：提供录音与播放管理器的单例，以支持聊天语音的录制与播放。与私密/云端模式配合，实现多模态交互路径。对应《最终作业.md》多模态挑战。
     * @author Persona Team <persona@project.local>
     * @version v1.0.0
     * @since 2025-11-30
     * @see com.example.persona.utils.AudioRecorderManager
     * @see com.example.persona.utils.AudioPlayerManager
     * @关联功能 REQ-C2 多模态交互-语音
     */

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
