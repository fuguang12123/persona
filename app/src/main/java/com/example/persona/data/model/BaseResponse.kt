package com.example.persona.data.model

import com.google.gson.annotations.SerializedName

// 通用的后端响应外壳
data class BaseResponse<T>(
    val code: Int,
    val message: String,
    @SerializedName("data") val data: T?
) {
    // 辅助方法：判断业务是否成功
    fun isSuccess(): Boolean = code == 200
}