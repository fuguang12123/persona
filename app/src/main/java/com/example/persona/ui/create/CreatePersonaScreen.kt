package com.example.persona.ui.create

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatePersonaScreen(
    viewModel: CreatePersonaViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var tagInput by remember { mutableStateOf("") }

    // 图片选择器
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadAvatar(it) }
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            Toast.makeText(context, if (uiState.isEditMode) "修改成功" else "创建成功", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "编辑智能体" else "创建智能体") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp).size(24.dp)
                        )
                    } else {
                        IconButton(onClick = { viewModel.submit() }) {
                            Icon(Icons.Default.Check, "Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // -----------------------------------------------------
            // 1. 头像上传区域 - 核心修改部分
            // -----------------------------------------------------
            Box(
                modifier = Modifier
                    .size(120.dp) // 稍微调大了尺寸，更易点击
                    .clip(CircleShape)
                    .background(Color.LightGray.copy(alpha = 0.5f))
                    .clickable {
                        // 无论是否有头像，点击都能触发选择
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.avatarUrl.isNotBlank()) {
                    // --- 场景 A：头像不为空，显示头像 ---

                    // 简单的处理逻辑：如果是 svg 结尾的 DiceBear 头像，尝试转 png 以便 Coil 更好渲染
                    // 也可以在后端处理，这里保留你的逻辑
                    val displayUrl = if (uiState.avatarUrl.contains(".svg")) {
                        uiState.avatarUrl.replace("/svg", "/png")
                    } else {
                        uiState.avatarUrl
                    }

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(displayUrl)
                            .crossfade(true)
                            // 可选：加载错误时显示默认占位符，避免变空白
                            .error(android.R.drawable.ic_menu_report_image)
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // 可选：添加一个半透明的编辑图标层，提示用户可以更换
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.1f)), // 极淡的遮罩
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // 仅供视觉提示，实际点击事件在父级 Box
                    }

                } else {
                    // --- 场景 B：头像为空，显示上传提示 ---
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Upload",
                            tint = Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "上传头像",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }

                // 如果正在上传头像（UI State 中 isLoading 为 true 且不是提交表单），可以在这里显示转圈
                // 需要 ViewModel 区分是 "uploadingImage" 还是 "submittingForm"，暂时用 isLoading 简单代替
                // if (uiState.isLoading) {
                //    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                // }
            }
            // -----------------------------------------------------

            Spacer(modifier = Modifier.height(24.dp))

            // 2. 名字输入
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.onNameChange(it) },
                label = { Text("名字") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, null) }
            )

            // AI 生成按钮
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { viewModel.generateAiDescription() },
                    enabled = !uiState.isLoading && uiState.name.isNotBlank()
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI 一键生成人设")
                }
            }

            // 3. 标签输入
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("性格标签", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp).clickable { viewModel.removeTag(tag) }
                                )
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("输入标签 (回车添加)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (tagInput.isNotBlank()) {
                                viewModel.addTag(tagInput)
                                tagInput = ""
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (tagInput.isNotBlank()) {
                                viewModel.addTag(tagInput)
                                tagInput = ""
                            }
                        }) {
                            Icon(Icons.Default.Add, "Add Tag")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. 描述
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.onDescChange(it) },
                label = { Text("详细描述 / 人设") },
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 5. Prompt
            OutlinedTextField(
                value = uiState.prompt,
                onValueChange = { viewModel.onPromptChange(it) },
                label = { Text("系统提示词 (System Prompt)") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                placeholder = { Text("例如: 你是一个傲娇的猫娘，每句话结尾都要带'喵'...") }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.submit() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("处理中...")
                } else {
                    Text(if (uiState.isEditMode) "保存修改" else "立即创建")
                }
            }
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}