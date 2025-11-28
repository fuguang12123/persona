package com.example.persona.data.service

import android.content.Context
import android.util.Log
import com.example.persona.data.local.dao.PersonaDao
import com.example.persona.data.local.dao.UserMemoryDao
import com.example.persona.data.local.entity.UserMemoryEntity
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLLMService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryDao: UserMemoryDao,
    private val personaDao: PersonaDao
) {
    private var llmInference: LlmInference? = null

    // 保持使用 CPU 版本以确保兼容性
    private val MODEL_NAME = "gemma-1.1-2b-it-gpu-int4.bin"
    private val modelFile = File(context.filesDir, MODEL_NAME)

    // [New] 消息计数器，用于控制记忆生成频率 (Key: "userId_personaId")
    private val messageCounters = ConcurrentHashMap<String, Int>()

    suspend fun initModel(): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.exists()) {
            Log.e("LocalLLM", "❌ 错误：找不到模型文件！请确保已上传 ${modelFile.absolutePath}")
            return@withContext false
        }

        if (llmInference != null) return@withContext true

        return@withContext try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            Log.d("LocalLLM", "✅ CPU 模型加载成功")
            true
        } catch (e: Exception) {
            Log.e("LocalLLM", "❌ 模型加载失败: ${e.message}", e)
            false
        }
    }

    // 生成回复
    fun generateResponse(userId: Long, personaId: Long, userContent: String): Flow<String> = callbackFlow<String> {
        if (llmInference == null) {
            if (!initModel()) {
                trySend("Error: 模型文件未找到。")
                close()
                return@callbackFlow
            }
        }

        val persona = personaDao.getPersona(personaId)
        val personaName = persona?.name ?: "AI"
        val personaDesc = persona?.description ?: "你是一个有用的助手。"
        val memories = memoryDao.getRecentMemories(userId, personaId, 5)

        val systemInstruction = """
            Instructions:
            You are playing the role of "$personaName".
            Your character description: $personaDesc
            
            Rules:
            1. Act as $personaName at all times.
            2. DO NOT introduce yourself or repeat your description unless asked.
            3. Answer the user's latest question directly and naturally.
        """.trimIndent()

        val memoryContext = if (memories.isNotEmpty()) {
            "\nRelated Memories:\n" + memories.joinToString("\n") { "- ${it.content}" }
        } else ""

        val fullPrompt = buildString {
            append("<start_of_turn>user\n")
            append(systemInstruction)
            append("\n")
            append(memoryContext)
            append("\n\nUser's latest message: $userContent\n")
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }

        try {
            llmInference?.generateResponseAsync(fullPrompt) { partialText, done ->
                if (!partialText.isNullOrEmpty()) {
                    trySend(partialText)
                }
                if (done) {
                    close()
                }
            }
        } catch (e: Exception) {
            trySend("Error: ${e.message}")
            close()
        }

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    // 总结记忆
    suspend fun summarizeAndSaveMemory(userId: Long, personaId: Long, chatContent: String) = withContext(Dispatchers.IO) {
        val key = "${userId}_${personaId}"
        val count = messageCounters.getOrDefault(key, 0) + 1
        messageCounters[key] = count

        // 阈值检查
        if (count % 5 != 0) {
            Log.d("LocalLLM", "Skip memory gen. Count: $count (Threshold: 5)")
            return@withContext
        }

        if (llmInference == null) return@withContext

        Log.d("LocalLLM", "🔄 Starting memory summarization...")

        // [Fix] 修改 Prompt：让 AI 返回 "None" 而不是 "无"，防止误伤包含 "无" 的中文句子（如 "无辣不欢"）
        val prompt = """<start_of_turn>user
Analysis Task:
Analyze the conversation below and extract specific details about the User (preferences, habits, plans, etc.).
- Output ONLY the extracted fact in a single sentence.
- If no useful information is found, output exactly "None".

Conversation:
$chatContent<end_of_turn>
<start_of_turn>model
"""
        try {
            val result = llmInference?.generateResponse(prompt)?.trim() ?: ""

            // [Debug] 打印原始结果，方便调试
            Log.d("LocalLLM", "📝 Raw Summary Result: '$result'")

            // [Fix] 优化过滤逻辑：
            // 1. 不为空
            // 2. 不是 "None" (忽略大小写)
            // 3. 长度适中 (放宽到 100 字符)
            val isValid =true

            if (isValid) {
                memoryDao.insertMemory(UserMemoryEntity(userId = userId, personaId = personaId, content = result))
                Log.d("LocalLLM", "🧠 ✅ Memory Saved: $result")
            } else {
                Log.d("LocalLLM", "🗑️ Memory Discarded (Invalid/None/Too Long)")
            }
        } catch (e: Exception) {
            Log.e("LocalLLM", "❌ Memory Gen Failed: ${e.message}")
            e.printStackTrace()
        }
    }
}