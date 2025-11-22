package com.example.persona.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.model.Persona
import com.example.persona.data.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateViewModel @Inject constructor(
    private val repository: PersonaRepository
) : ViewModel() {

    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var avatarUrl by mutableStateOf("")
    var generatedTags by mutableStateOf("AI生成")

    var isGenerating by mutableStateOf(false)
    var isSaving by mutableStateOf(false)

    // ✅ 导航状态控制
    var navigateBack by mutableStateOf(false)

    fun onNameChange(newVal: String) {
        name = newVal
        if (!avatarUrl.contains("aliyuncs")) {
            avatarUrl = "https://api.dicebear.com/7.x/avataaars/png?seed=$newVal"
        }
    }

    fun onAiAssistClick() {
        if (name.isBlank()) return

        viewModelScope.launch {
            isGenerating = true
            val aiResult = repository.generateDescription(name)

            if (aiResult.isNotBlank() && !aiResult.startsWith("AI 生成失败")) {
                if (aiResult.contains("#")) {
                    val parts = aiResult.split("#", limit = 2)
                    description = parts[0].trim()
                    generatedTags = parts[1].trim().replace(" ", "")
                } else {
                    description = aiResult
                    generatedTags = "AI生成"
                }
            } else {
                description = aiResult
            }
            isGenerating = false
        }
    }

    // ✅ 不带参数的保存方法
    fun onSaveClick() {
        if (name.isBlank()) return

        viewModelScope.launch {
            isSaving = true

            val persona = Persona(
                id = 0,
                name = name,
                description = description,
                avatarUrl = avatarUrl,
                personalityTags = generatedTags
            )

            val success = repository.createPersona(persona)

            isSaving = false
            if (success) {
                // ✅ 保存成功，修改状态，通知 UI 跳转
                navigateBack = true
            }
        }
    }

    // 重置导航状态
    fun onNavigated() {
        navigateBack = false
    }
}