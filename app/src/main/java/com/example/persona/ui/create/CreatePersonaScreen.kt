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

// @Composable 注解表示这是一个构建 UI 的函数，Compose 框架会调用它来渲染界面
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatePersonaScreen(
    // hiltViewModel() 自动获取业务逻辑控制器 (ViewModel)，这里是依赖注入
    viewModel: CreatePersonaViewModel = hiltViewModel(),
    // onBack 是一个回调函数，当需要返回上一页时调用它
    onBack: () -> Unit
) {
    // 1. 状态收集：监听 ViewModel 中的 uiState 变化
    // 当 ViewModel 更新数据时，这里会自动刷新界面
    val uiState by viewModel.uiState.collectAsState()

    // 获取当前 Android 上下文，用于显示 Toast 提示
    val context = LocalContext.current

    // 定义一个临时的 UI 状态，用于记录标签输入框的内容
    // remember { ... } 保证了界面重绘时这个变量不会被重置
    var tagInput by remember { mutableStateOf("") }

    // 2. 图片选择器配置
    // 注册一个系统活动结果启动器，用于打开相册选择图片
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        // 当用户选好图片后，代码会执行到这里
        // uri 是图片的路径，如果不为空，就传给 ViewModel 上传
        uri?.let { viewModel.uploadAvatar(it) }
    }

    // 3. 副作用监听：监听 isSuccess 状态
    // LaunchedEffect 是处理"一次性事件"的地方
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            // 弹出提示框
            Toast.makeText(context, if (uiState.isEditMode) "修改成功" else "创建成功", Toast.LENGTH_SHORT).show()
            // 调用返回函数，关闭当前页面
            onBack()
        }
    }

    // 4. 副作用监听：监听 error 状态
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            // 如果有错误消息，弹出提示
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    // Scaffold 是页面的脚手架，提供了顶部栏(TopBar)和内容区域(content)的标准结构
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "编辑智能体" else "创建智能体") },
                navigationIcon = {
                    // 返回按钮
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // 右上角的动作按钮区域
                    if (uiState.isLoading) {
                        // 如果正在加载，显示转圈圈
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp).size(24.dp)
                        )
                    } else {
                        // 否则显示打钩保存按钮，点击触发 submit
                        IconButton(onClick = { viewModel.submit() }) {
                            Icon(Icons.Default.Check, "Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        // 页面主体内容
        // Column 表示垂直布局，里面的元素从上到下排列
        Column(
            modifier = Modifier
                .padding(padding) // 避开 TopBar 的高度
                .fillMaxSize()    // 占满屏幕
                .padding(16.dp)   // 内容距离屏幕边缘 16dp
                .verticalScroll(rememberScrollState()), // 允许垂直滚动
            horizontalAlignment = Alignment.CenterHorizontally // 内容水平居中
        ) {
            // -----------------------------------------------------
            // 1. 头像上传区域
            // -----------------------------------------------------
            Box(
                modifier = Modifier
                    .size(120.dp) // 设置大小
                    .clip(CircleShape) // 裁剪成圆形
                    .background(Color.LightGray.copy(alpha = 0.5f)) // 灰色背景
                    .clickable {
                        // 点击头像区域，启动相册选择器，只显示图片
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape), // 边框
                contentAlignment = Alignment.Center // 内容居中
            ) {
                if (uiState.avatarUrl.isNotBlank()) {
                    // --- 场景 A：头像有 URL，显示图片 ---

                    // 简单的处理逻辑：把 svg 换成 png，防止 Android 显示不出
                    val displayUrl = if (uiState.avatarUrl.contains(".svg")) {
                        uiState.avatarUrl.replace("/svg", "/png")
                    } else {
                        uiState.avatarUrl
                    }

                    // AsyncImage 是 Coil 库的组件，用于加载网络图片
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(displayUrl)
                            .crossfade(true) // 淡入动画
                            .error(android.R.drawable.ic_menu_report_image) // 加载失败显示的图
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop // 裁剪模式：填满
                    )

                    // 添加一个极淡的黑色遮罩，让它看起来可以点击
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                    }

                } else {
                    // --- 场景 B：没有头像，显示加号图标和文字 ---
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Upload",
                            tint = Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp)) // 间距
                        Text(
                            "上传头像",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            // -----------------------------------------------------

            Spacer(modifier = Modifier.height(24.dp))

            // 2. 名字输入框
            OutlinedTextField(
                value = uiState.name, // 绑定当前名字
                onValueChange = { viewModel.onNameChange(it) }, // 当用户打字时，通知 ViewModel 更新名字
                label = { Text("名字") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true, // 只能输一行
                leadingIcon = { Icon(Icons.Default.Person, null) } // 左侧图标
            )

            // AI 生成按钮区域
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { viewModel.generateAiDescription() }, // 点击触发 AI 生成
                    // 按钮是否可用：必须不在加载中，且名字已经填了
                    enabled = !uiState.isLoading && uiState.name.isNotBlank()
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI 一键生成人设")
                }
            }

            // 3. 标签输入区域
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("性格标签", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                // FlowRow: 像水流一样的布局，一行放不下自动换行
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 遍历所有已有的标签，显示出来
                    uiState.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { },
                            label = { Text(tag) },
                            // 尾部显示一个小叉号，点击删除这个标签
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

                // 输入新标签的框
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("输入标签 (回车添加)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    // 键盘配置：把回车键变成 "完成"
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    // 键盘动作：点击 "完成" 时触发添加标签
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (tagInput.isNotBlank()) {
                                viewModel.addTag(tagInput)
                                tagInput = "" // 清空输入框
                            }
                        }
                    ),
                    // 右侧加一个添加按钮
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

            // 4. 详细描述输入框 (多行)
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.onDescChange(it) },
                label = { Text("详细描述 / 人设") },
                modifier = Modifier.fillMaxWidth().height(150.dp) // 高度固定 150dp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 5. System Prompt 输入框
            OutlinedTextField(
                value = uiState.prompt,
                onValueChange = { viewModel.onPromptChange(it) },
                label = { Text("系统提示词 (System Prompt)") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                placeholder = { Text("例如: 你是一个傲娇的猫娘，每句话结尾都要带'喵'...") }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 底部的大按钮
            Button(
                onClick = { viewModel.submit() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !uiState.isLoading // 加载时禁用
            ) {
                if (uiState.isLoading) {
                    // 显示加载转圈
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("处理中...")
                } else {
                    // 根据模式显示文字
                    Text(if (uiState.isEditMode) "保存修改" else "立即创建")
                }
            }
            Spacer(modifier = Modifier.height(50.dp)) // 底部留白
        }
    }
}