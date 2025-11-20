package com.example.persona.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.model.Persona
import com.example.persona.data.remote.AiGenRequest
import com.example.persona.data.remote.PersonaService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateViewModel @Inject constructor(
    private val personaService: PersonaService
) : ViewModel() {

    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var avatarUrl by mutableStateOf("")

    // ✅ 新增：用于临时存储 AI 生成的标签
    // 虽然 UI 上可能没有输入框显示它，但保存时会用到
    var generatedTags by mutableStateOf("AI生成")

    var isGenerating by mutableStateOf(false)
    var isSaving by mutableStateOf(false)

    fun onNameChange(newVal: String) {
        name = newVal
        if (!avatarUrl.contains("aliyuncs")) {
            avatarUrl = "https://api.dicebear.com/7.x/avataaars/svg?seed=$newVal"
        }
    }

    // 🧠 核心修改：解析 AI 返回的 "描述 #标签" 格式
    fun onAiAssistClick() {
        if (name.isBlank()) return

        println("DEBUG: Clicked AI Assist with name: $name")

        viewModelScope.launch {
            isGenerating = true
            try {
                val res = personaService.generatePersonaDescription(AiGenRequest(name))

                if (res.isSuccessful && res.body()?.code == 200) {
                    val rawText = res.body()?.data ?: ""
                    println("DEBUG: AI Raw Output: $rawText")

                    // ✅ 字符串切割逻辑
                    if (rawText.contains("#")) {
                        val parts = rawText.split("#", limit = 2)
                        // 第一部分填入描述框
                        description = parts[0].trim()
                        // 第二部分存入标签变量
                        generatedTags = parts[1].trim().replace(" ", "")
                        println("DEBUG: Parsed Tags: $generatedTags")
                    } else {
                        // 兼容逻辑：如果 AI 没按格式返回
                        description = rawText
                        generatedTags = "AI生成"
                    }
                } else {
                    val errorMsg = "生成失败: ${res.code()} ${res.message()}"
                    println("DEBUG: $errorMsg")
                    description = errorMsg
                }
            } catch (e: Exception) {
                e.printStackTrace()
                description = "网络错误: ${e.message}"
            } finally {
                isGenerating = false
            }
        }
    }

    // 💾 保存按钮
    fun onSaveClick(onSuccess: () -> Unit) {
        viewModelScope.launch {
            isSaving = true
            try {
                val persona = Persona(
                    id = 0,
                    name = name,
                    description = description,
                    avatarUrl = avatarUrl,
                    // ✅ 这里使用 AI 生成的标签
                    tags = generatedTags
                )

                val res = personaService.createPersona(persona)
                if (res.isSuccessful && res.body()?.code == 200) {
                    onSuccess()
                } else {
                    println("DEBUG: Save Failed: ${res.code()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSaving = false
            }
        }
    }
}