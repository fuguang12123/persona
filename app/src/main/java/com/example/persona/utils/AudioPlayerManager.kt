package com.example.persona.utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * 播放管理器
 * 统一管理 MediaPlayer，避免多个声音重叠
 * 采用 SSOT 思想，UI 只需要观察 currentPlayingUrl
 */
class AudioPlayerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    // 当前正在播放的 URL 或 路径 (用于 UI 显示暂停图标)
    private val _currentPlayingUrl = MutableStateFlow<String?>(null)
    val currentPlayingUrl = _currentPlayingUrl.asStateFlow()

    fun play(urlOrPath: String) {
        // 如果点的就是当前正在放的，则暂停/停止
        if (_currentPlayingUrl.value == urlOrPath) {
            stop()
            return
        }

        // 停止之前的
        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(urlOrPath)
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    _currentPlayingUrl.value = null
                    release()
                    mediaPlayer = null
                }
                setOnErrorListener { _, _, _ ->
                    _currentPlayingUrl.value = null
                    true
                }
                prepareAsync()
            }
            // 立即更新状态，UI 会显示为播放中
            _currentPlayingUrl.value = urlOrPath
        } catch (e: IOException) {
            Log.e("AudioPlayer", "Play failed", e)
            _currentPlayingUrl.value = null
        }
    }

    fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            _currentPlayingUrl.value = null
        }
    }

    // 辅助方法：判断是否正在播放某 URL
    fun isPlaying(url: String): Boolean {
        return _currentPlayingUrl.value == url
    }
}