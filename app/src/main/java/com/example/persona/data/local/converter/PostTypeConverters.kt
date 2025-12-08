package com.example.persona.data.local.converter

import androidx.room.TypeConverter
import org.json.JSONArray

/**
 * Room 类型转换器
 */
class PostTypeConverters {

    @TypeConverter
    fun fromStringList(images: List<String>?): String {
        if (images == null) return "[]"
        val jsonArray = JSONArray()
        // 修正：由于上面已经进行了 null 检查并返回，这里 images 被智能转换为非空类型
        // 因此不需要安全调用符号 (?.)，直接使用 (.) 即可
        images.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        if (data == null) return emptyList()
        val list = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(data)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}