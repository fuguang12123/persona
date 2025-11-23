package com.example.persona.ui.create

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    viewModel: CreatePostViewModel = hiltViewModel(),
    personaId: Long = 1L,
    onBackClick: () -> Unit,
    onPostSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // [New] 控制大图预览 Dialog 的状态
    var showFullImage by remember { mutableStateOf(false) }

    // 相册选择器
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadLocalImage(it) }
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            Toast.makeText(context, "发布成功", Toast.LENGTH_SHORT).show()
            onPostSuccess()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // [New] 大图预览 Dialog
    if (showFullImage && uiState.imageUrl != null) {
        Dialog(
            onDismissRequest = { showFullImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false) // 全屏
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showFullImage = false }, // 点击任意处关闭
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = uiState.imageUrl,
                    contentDescription = "Full Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
                // 关闭按钮
                IconButton(
                    onClick = { showFullImage = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("发布动态") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.createPost(personaId) },
                        enabled = !uiState.isLoading && uiState.content.isNotBlank(),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text("发布")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )
        }
    ) { padding ->
        // [New] 使用 verticalScroll 让内容自然滚动，解决图片被挤到底部的问题
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()) // 关键：可滚动
                .padding(16.dp)
        ) {
            // 1. 文本框
            OutlinedTextField(
                value = uiState.content,
                onValueChange = viewModel::onContentChange,
                placeholder = { Text("此刻的想法... (支持AI配图或本地上传)") },
                // [New] 移除 weight(1f)，设置最小高度，使其随内容或固定高度
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 图片预览区 (紧跟文字下方)
            if (uiState.imageUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight() // 高度自适应
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.LightGray)
                        .clickable { showFullImage = true } // [New] 点击查看大图
                ) {
                    AsyncImage(
                        model = uiState.imageUrl,
                        contentDescription = "Image",
                        contentScale = ContentScale.FillWidth, // 宽度填满，高度自适应
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 标签和删除按钮容器
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 来源标签
                        AssistChip(
                            onClick = {},
                            label = { Text(if (uiState.isAiGenerated) "AI 生成" else "本地上传") },
                            colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.9f))
                        )

                        // 删除按钮
                        IconButton(
                            onClick = { viewModel.removeImage() },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                        ) {
                            Icon(Icons.Default.Close, "Remove", tint = Color.White)
                        }
                    }
                }
            }

            // 留出底部按钮的空间，或者直接把按钮放在这里
            Spacer(modifier = Modifier.height(24.dp))

            // 3. 底部工具栏 (紧跟内容)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AI 配图
                SuggestionChip(
                    onClick = { viewModel.generateAiImage() },
                    label = { Text("AI 配图") },
                    icon = { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.height(48.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 上传图片
                SuggestionChip(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    label = { Text("相册上传") },
                    icon = { Icon(Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.secondary) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.height(48.dp)
                )

                if (uiState.isLoading && uiState.imageUrl == null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("处理中...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }

            // 底部增加一些额外空白，防止滚动到底部时太贴边
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}