package com.example.persona.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 录音管理器
 * 核心配置: SampleRate 24000, Mono, PCM 16bit -> WAV
 */
class AudioRecorderManager(private val context: Context) {

    private val SAMPLE_RATE = 24000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    private var outputFile: File? = null
    private var startTime = 0L

    // 暴露给 UI 的音量状态 (用于波纹动画 0~1)
    private val _amplitude = MutableStateFlow(0f)
    val amplitude = _amplitude.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(fileName: String = "temp_audio.wav"): Boolean {
        // 二次检查权限
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("AudioRecorder", "Permission denied")
            return false
        }

        try {
            outputFile = File(context.cacheDir, fileName)
            if (outputFile!!.exists()) outputFile!!.delete()

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE
            )

            audioRecord?.startRecording()
            isRecording = true
            startTime = System.currentTimeMillis()

            // 开启后台协程写入数据
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                writeAudioDataToFile()
            }
            return true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Start failed", e)
            return false
        }
    }

    /**
     * 停止录音
     * @return Pair(文件, 时长秒数)
     */
    suspend fun stopRecording(): Pair<File, Int>? {
        if (!isRecording) return null

        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        recordingJob?.join() // 等待写入完成
        audioRecord = null

        val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()

        // 关键步骤：给 PCM 数据加上 WAV 头
        val rawFile = File(outputFile!!.absolutePath + ".raw") // 临时原始文件
        if (rawFile.exists() && outputFile != null) {
            copyRawToWavFile(rawFile, outputFile!!)
            rawFile.delete() // 删除原始 PCM 数据
            return Pair(outputFile!!, duration)
        }

        return null
    }

    fun cancelRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null
        recordingJob?.cancel()

        // 删除临时文件
        outputFile?.let {
            val raw = File(it.absolutePath + ".raw")
            if (raw.exists()) raw.delete()
            if (it.exists()) it.delete()
        }
        _amplitude.value = 0f
    }

    private fun writeAudioDataToFile() {
        // 先写到 .raw 文件，因为此时不知道最终文件大小，无法写 WAV 头
        val rawFile = File(outputFile!!.absolutePath + ".raw")
        val data = ByteArray(BUFFER_SIZE)

        try {
            FileOutputStream(rawFile).use { os ->
                while (isRecording) {
                    val read = audioRecord?.read(data, 0, BUFFER_SIZE) ?: 0
                    if (read > 0) {
                        os.write(data, 0, read)

                        // 计算简单的音量 (Root Mean Square)
                        var sum = 0.0
                        for (i in 0 until read step 2) {
                            val sample = (data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)
                            sum += sample.toShort() * sample.toShort()
                        }
                        val rms = Math.sqrt(sum / (read / 2))
                        // 归一化并更新状态
                        _amplitude.value = (rms / 32768.0).coerceIn(0.0, 1.0).toFloat()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Write failed", e)
        }
    }

    // 添加 WAV 文件头
    private fun copyRawToWavFile(rawFile: File, wavFile: File) {
        val rawData = rawFile.readBytes()
        val totalDataLen = rawData.size + 36
        val byteRate = SAMPLE_RATE * 1 * 16 / 8

        val header = ByteBuffer.allocate(44)
        header.order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen) // File size - 8
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // Chunk size
        header.putShort(1) // AudioFormat (1 = PCM)
        header.putShort(1) // Channels (Mono)
        header.putInt(SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort(2) // Block align
        header.putShort(16) // Bits per sample
        header.put("data".toByteArray())
        header.putInt(rawData.size)

        FileOutputStream(wavFile).use { os ->
            os.write(header.array())
            os.write(rawData)
        }
    }
}