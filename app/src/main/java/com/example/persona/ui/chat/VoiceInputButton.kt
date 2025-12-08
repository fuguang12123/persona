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
 * **极速响应版语音按钮 (Zero-Latency Voice Button)**
 *
 * ## 核心痛点解决
 * 普通的 Compose 手势检测（如 `clickable` 或 `detectTapGestures`）为了区分单击、双击和长按，
 * 默认会有约 100ms 的判定延迟。这在语音即时通讯场景下是不可接受的。
 *
 * ## 技术实现
 * 本组件绕过了高层封装，直接使用底层 `pointerInput` + `awaitPointerEventScope`：
 * 1. **零延迟响应**: 监听原始 `ACTION_DOWN` 事件，手指触屏瞬间即触发录音。
 * 2. **手势状态机**: 手动管理 Down -> Move -> Up 的完整生命周期。
 * 3. **上滑取消**: 在 Move 阶段实时计算 Y 轴偏移量，实现类似微信的"上滑取消"交互。
 */
@Composable
fun VoiceInputButton(
    viewModel: ChatViewModel,
    onStartRecording: () -> Boolean, // 返回 true 表示录音成功启动
    onStopRecording: () -> Unit,     // 正常发送
    onCancelRecording: () -> Unit    // 取消发送
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // 从 ViewModel 获取状态，驱动 UI 变化 (SSOT 原则)
    val isRecording = viewModel.isRecording
    val isCancelling = viewModel.isVoiceCancelling

    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) Toast.makeText(context, "权限获取成功，请再次按住说话", Toast.LENGTH_SHORT).show()
    }

    // 计算触发取消的阈值：-100dp (向上滑动为负值)
    // 预先转为像素，避免在手势循环中重复计算
    val cancelThreshold = with(density) { -100.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            // 视觉反馈：根据录音状态改变背景色
            .background(if (isRecording) Color.LightGray else MaterialTheme.colorScheme.surfaceVariant)
            // 🔥 核心手势逻辑区域
            .pointerInput(Unit) {
                // awaitEachGesture 确保每次手指按下都会启动一个新的协程作用域来处理手势
                // 即使手势被中断或取消，下次按下时也能重新开始
                awaitEachGesture {
                    // --- 阶段 1: 等待按下 (ACTION_DOWN) ---

                    // awaitFirstDown 是挂起函数，会暂停直到第一个手指按下。
                    // 它是零延迟的关键。
                    val down = awaitFirstDown(requireUnconsumed = false)

                    // 权限检查前置：没有权限直接请求并中断本次手势
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@awaitEachGesture
                    }

                    // --- 阶段 2: 触发录音 ---
                    val started = onStartRecording()

                    if (started) {
                        // 触觉反馈：给予用户"开始工作"的物理确认
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        // 局部变量跟踪本次手势是否处于取消区域
                        // 不直接依赖 ViewModel 是为了在手势循环中做逻辑判断
                        var isCancelState = false

                        // --- 阶段 3: 循环监听移动 (ACTION_MOVE) ---
                        do {
                            // 挂起等待下一个指针事件 (Move 或 Up)
                            val event = awaitPointerEvent()
                            // 获取当前手指的变化信息
                            val change = event.changes.lastOrNull()

                            if (change != null && change.pressed) {
                                // 计算 Y 轴偏移 (相对于组件左上角)
                                // 在 Android 坐标系中，向上滑动 Y 变小 (负数)
                                val currentY = change.position.y

                                // 判定是否进入取消区域
                                val newCancelState = currentY < cancelThreshold

                                // [状态去抖动 Logic Debounce]
                                // 只有当状态真正发生变化时，才更新 ViewModel 和触发震动
                                // 避免在阈值临界点反复触发
                                if (newCancelState != isCancelState) {
                                    isCancelState = newCancelState

                                    // 更新 ViewModel，这会立刻驱动 Overlay 显示"红色垃圾桶"或"麦克风"
                                    viewModel.isVoiceCancelling = isCancelState

                                    // 状态切换的触觉反馈
                                    if (isCancelState) {
                                        // 进入取消区：明显震动 (警告)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else {
                                        // 回到录音区：轻微震动 (恢复)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                            // 只要还有手指按着 (pressed)，就继续循环
                        } while (event.changes.any { it.pressed })

                        // --- 阶段 4: 手指抬起 (ACTION_UP) ---
                        // 循环结束意味着手指离开了屏幕

                        if (isCancelState) {
                            // 如果是在取消区域松手 -> 丢弃
                            onCancelRecording()
                        } else {
                            // 如果是在正常区域松手 -> 发送
                            onStopRecording()
                        }

                        // 重置 ViewModel 状态，隐藏 Overlay
                        viewModel.isVoiceCancelling = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 按钮内部文本，根据状态变化
        Text(
            text = if (isCancelling) "松开取消" else if (isRecording) "松开发送" else "按住 说话",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isRecording) Color.Black else MaterialTheme.colorScheme.onSurface
        )
    }
}