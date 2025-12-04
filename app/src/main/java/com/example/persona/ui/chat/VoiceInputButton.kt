package com.example.persona.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * 极速响应版语音按钮
 * 使用底层 pointerInput 实现"按下即录"，消除 detectDragGestures 的启动延迟。
 */
@Composable
fun VoiceInputButton(
    viewModel: ChatViewModel,
    onStartRecording: () -> Boolean,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // 从 ViewModel 获取状态
    val isRecording = viewModel.isRecording
    val isCancelling = viewModel.isVoiceCancelling

    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) Toast.makeText(context, "权限获取成功，请再次按住说话", Toast.LENGTH_SHORT).show()
    }

    // 触发取消的阈值 (上滑 100dp)
    val cancelThreshold = with(density) { -100.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isRecording) Color.LightGray else MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                awaitEachGesture {
                    // 1. 等待第一根手指按下 (ACTION_DOWN)，零延迟
                    val down = awaitFirstDown(requireUnconsumed = false)

                    // 权限检查前置
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@awaitEachGesture
                    }

                    // 2. 立即触发录音
                    val started = onStartRecording()

                    if (started) {
                        // 震动反馈：告知用户录音开始
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        var isCancelState = false

                        // 3. 循环监听移动事件 (ACTION_MOVE)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.lastOrNull()

                            if (change != null && change.pressed) {
                                // 计算 Y 轴偏移 (相对于按钮组件左上角)
                                // 向上滑 Y 会变小 (负数)
                                val currentY = change.position.y

                                // 判断是否进入取消区域
                                val newCancelState = currentY < cancelThreshold

                                // 状态去抖动：只有状态真正改变时才更新，防止边缘反复跳变
                                if (newCancelState != isCancelState) {
                                    isCancelState = newCancelState
                                    viewModel.isVoiceCancelling = isCancelState

                                    // 状态切换震动反馈
                                    if (isCancelState) {
                                        // 进入取消区：明显震动
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else {
                                        // 回到录音区：轻微震动
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // 4. 手指抬起 (ACTION_UP) 或 事件取消
                        // 再次读取最新的状态 (循环结束后 isCancelState 可能不是最新的，以 ViewModel 为准或使用局部变量)
                        if (isCancelState) {
                            onCancelRecording()
                        } else {
                            onStopRecording()
                        }

                        // 重置 ViewModel 状态
                        viewModel.isVoiceCancelling = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isCancelling) "松开取消" else if (isRecording) "松开发送" else "按住 说话",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isRecording) Color.Black else MaterialTheme.colorScheme.onSurface
        )
    }
}