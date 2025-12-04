package com.example.persona.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 录音全屏浮层
 * 显示在界面最上层，根据 isCancelling 状态改变颜色和图标
 */
@Composable
fun VoiceRecordingOverlay(
    isCancelling: Boolean,
    amplitude: Float // 0f ~ 1f，用于音量动画
) {
    // 动态背景色：取消时变红，正常时黑色半透明
    val backgroundColor by animateColorAsState(
        targetValue = if (isCancelling) Color(0xFFB00020).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.7f),
        label = "bgColor"
    )

    // 动态图标大小：根据音量振幅跳动 (仅在非取消状态下生效)
    // 基础大小 64dp，最大增加 40dp
    val targetSize = if (isCancelling) 64.dp else (64.dp + (40.dp * amplitude))
    val animatedSize by animateDpAsState(targetValue = targetSize, label = "iconSize")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent), // 这里设透明，实际上是内部 Box 有颜色
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(backgroundColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 图标区域
                Icon(
                    imageVector = if (isCancelling) Icons.Default.Delete else Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(animatedSize)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 提示文字
                Text(
                    text = if (isCancelling) "松开手指，取消发送" else "手指上滑，取消发送",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCancelling) Color.White else Color(0xFFCCCCCC)
                )
            }
        }
    }
}