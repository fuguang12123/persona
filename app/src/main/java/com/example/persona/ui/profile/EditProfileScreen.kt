package com.example.persona.ui.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

/**
 * 编辑个人资料界面
 * 支持修改昵称、头像和背景图,并在未保存时提示用户
 *
 * @param viewModel 编辑资料视图模型,使用Hilt注入
 * @param onBack 返回按钮点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: EditProfileViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 控制是否显示"未保存退出"的确认弹窗
    var showExitDialog by remember { mutableStateOf(false) }

    /**
     * 处理返回逻辑的函数
     * 如果有未保存的修改,显示确认弹窗;否则直接返回
     */
    val handleBack = {
        if (uiState.hasChanges) {
            showExitDialog = true
        } else {
            onBack()
        }
    }

    /**
     * 拦截系统物理返回键/手势
     * 当有未保存的修改时,阻止直接返回,显示确认弹窗
     */
    BackHandler(enabled = uiState.hasChanges) {
        showExitDialog = true
    }

    /**
     * 头像选择器
     * 使用系统图片选择器,选择后上传
     */
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let { viewModel.uploadImage(it, isAvatar = true) } }

    /**
     * 背景图选择器
     * 使用系统图片选择器,选择后上传
     */
    val bgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let { viewModel.uploadImage(it, isAvatar = false) } }

    // 监听保存成功状态,成功后显示提示并返回
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    /**
     * 未保存退出的确认弹窗
     * 提示用户有未保存的修改,询问是否确认退出
     */
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("提示") },
            text = { Text("修改还未保存,是否退出?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onBack() // 确认退出,不保存
                    }
                ) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑资料") },
                navigationIcon = {
                    // 点击左上角返回箭头,走拦截逻辑
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.isLoading) {
                        // 上传中显示加载指示器
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        // 保存按钮,只有有修改时才可用
                        IconButton(
                            onClick = { viewModel.saveProfile() },
                            enabled = uiState.hasChanges
                        ) {
                            Icon(
                                Icons.Default.Check,
                                "Save",
                                tint = if (uiState.hasChanges) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 背景图与头像区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                // 背景图,点击可更换
                AsyncImage(
                    model = uiState.backgroundImageUrl.ifBlank { "https://picsum.photos/800/400" },
                    contentDescription = "Background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                )
                // 背景图编辑提示遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, null, tint = Color.White.copy(alpha = 0.7f))
                }

                // 头像,居中底部,点击可更换
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 40.dp) // 下移一半,形成层叠效果
                ) {
                    AsyncImage(
                        model = uiState.avatarUrl.ifBlank { "" },
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray)
                            .clickable {
                                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(50.dp)) // 让出头像位置

            // 2. 表单区域
            Column(modifier = Modifier.padding(16.dp)) {
                // 昵称输入框
                OutlinedTextField(
                    value = uiState.nickname,
                    onValueChange = { viewModel.onNicknameChange(it) },
                    label = { Text("昵称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 显示错误信息
                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
