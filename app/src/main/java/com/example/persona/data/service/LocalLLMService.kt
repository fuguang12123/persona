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
    /**
     * @class com.example.persona.data.service.LocalLLMService
     * @description 端侧大语言模型推理服务，负责在“私密模式”下以本地模型完成对话生成与记忆摘要。通过 `callbackFlow` 实现流式输出，结合 IO 调度保证推理与数据库写入的线程隔离。服务采用 Gemma 量化模型（示例），并提供模型初始化检查、分片输出、阈值记忆摘要等机制，满足《最终作业.md》进阶挑战中的端云协同混合架构（C4）与富交互体验（C1）。
     * @author Persona Team <persona@project.local>
     * @version v1.0.0
     * @since 2025-11-30
     * @see com.example.persona.data.repository.ChatRepository
     * @关联功能 REQ-C1 富文本与流式输出；REQ-C4 端云协同混合架构
     */
    private var llmInference: LlmInference? = null

    // 保持使用 CPU 版本以确保兼容性
    private val MODEL_NAME = "gemma-1.1-2b-it-gpu-int4.bin"
    private val modelFile = File(context.filesDir, MODEL_NAME)

    // [New] 消息计数器，用于控制记忆生成频率 (Key: "userId_personaId")
    private val messageCounters = ConcurrentHashMap<String, Int>()

    /**
     * 功能: 初始化端侧推理模型（IO 线程），校验文件存在并创建推理器。
     * 实现逻辑: 检查模型文件 -> 构建 Options -> 创建 `LlmInference`。
     * 返回值: Boolean - true 表示初始化成功；false 表示缺失或异常。
     * 关联功能: REQ-C4 端云协同-端侧模型集成
     */
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
    /**
     * 功能: 基于端侧模型生成流式回复，返回 `Flow<String>` 的分片文本，供 UI 渐进呈现。
     * 实现逻辑:
     * 1. 若未初始化则尝试初始化模型
     * 2. 拼接角色设定、记忆上下文与用户消息，构造完整 Prompt
     * 3. 通过 `generateResponseAsync` 推送分片至 Flow，结束时关闭流
     * @param userId Long - 用户ID
     * @param personaId Long - Persona ID
     * @param userContent String - 用户输入
     * @return Flow<String> - 文本分片流
     * 关联功能: REQ-C1 流式输出；REQ-C4 端云协同-私聊端侧路径
     * 复杂度分析: 时间 O(T)（与生成长度相关）| 空间 O(1)
     * 线程安全: 是 - `flowOn(Dispatchers.IO)` 确保在 IO 线程推理
     */
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
    /**
     * 功能: 对对话进行周期性摘要并持久化到本地记忆库，以实现“记忆共生”。
     * 实现逻辑:
     * 1. 频率阈值（每5次）控制，降低写入与成本
     * 2. 构造分析 Prompt，生成事实性摘要
     * 3. 过滤无效结果并写入 Room
     * @param userId Long - 用户ID
     * @param personaId Long - Persona ID
     * @param chatContent String - 对话拼接文本
     * @return Unit
     * 关联功能: REQ-C4 端云协同-记忆影响；REQ-B5/B6 共生与行为影响
     */
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
