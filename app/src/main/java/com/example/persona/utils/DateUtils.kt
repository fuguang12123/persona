package com.example.persona.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {

    fun formatTimeAgo(timeStr: String?): String {
        if (timeStr.isNullOrBlank()) return ""

        try {
            // 1. 解析时间（强制理解为北京时间）
            val date = parseDate(timeStr) ?: return timeStr

            // 2. 获取当前时间（UTC时间戳，全球统一）
            val now = System.currentTimeMillis()

            // 3. 计算差值
            val diff = now - date.time

            // 调试用：如果还不对，可以打印 diff 看看是不是负数
            // Log.d("DateUtils", "Now: $now, Date: ${date.time}, Diff: $diff")

            if (diff < 0) return "刚刚" // 只有真正的时间倒流才显示刚刚

            return when {
                diff < 60 * 1000 -> "刚刚" // < 1分钟
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前" // < 1小时
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前" // < 24小时
                diff < 48 * 60 * 60 * 1000 -> "昨天" // < 48小时 (这里放宽到48小时内算昨天)
                else -> {
                    val outputSdf = SimpleDateFormat("MM-dd", Locale.getDefault())
                    outputSdf.format(date)
                }
            }
        } catch (e: Exception) {
            return if (timeStr.length >= 10) timeStr.substring(5, 10) else timeStr
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val date = Date(timestamp)
        val outputSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return outputSdf.format(date)
    }

    private fun parseDate(timeStr: String): Date? {
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS"
        )

        // [Fix] 核心修复：后端是北京时间，所以这里必须强制用 Asia/Shanghai 解析
        // 这样无论手机调成什么时区，都能正确算出这个时间点的绝对时间戳
        val serverTimeZone = TimeZone.getTimeZone("Asia/Shanghai")

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.timeZone = serverTimeZone
                return sdf.parse(timeStr)
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }
}