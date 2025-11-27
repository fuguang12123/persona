package com.example.persona.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat

@Composable
fun VoiceInputButton(
    onStartRecording: () -> Boolean,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    isRecording: Boolean
) {
    val context = LocalContext.current
    var isCancelling by remember { mutableStateOf(false) } // 是否处于“松开取消”区域

    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(context, "权限已获取，请再次按住说话", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "需要录音权限才能发送语音", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isRecording) Color.LightGray else MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // 1. 检查权限
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@detectTapGestures
                        }

                        // 2. 开始录音
                        val started = onStartRecording()
                        if (started) {
                            try {
                                isCancelling = false
                                awaitRelease() // 等待手指抬起
                            } finally {
                                // 3. 手指抬起后的逻辑
                                if (isCancelling) {
                                    onCancelRecording()
                                } else {
                                    onStopRecording()
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                // 检测拖拽，判断是否上滑取消
                detectDragGesturesAfterLongPress(
                    onDragStart = { },
                    onDragEnd = { },
                    onDragCancel = { },
                    onDrag = { change, dragAmount ->
                        // Y轴向上偏移超过 50dp 视为取消
                        // change.position 是相对于组件左上角的坐标，负数表示向上
                        // 这里的逻辑简化处理：判断 Y 坐标是否小于 -100 (向上滑出按钮一定距离)
                        isCancelling = change.position.y < -100f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isRecording) (if (isCancelling) "松开取消" else "松开发送") else "按住 说话",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }

    // 录音时的全屏 Overlay (类似微信中间那个弹窗)
    if (isRecording) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(
                            color = if (isCancelling) Color.Red.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isCancelling) Icons.Default.Undo else Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isCancelling) "松开手指，取消发送" else "手指上滑，取消发送",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}