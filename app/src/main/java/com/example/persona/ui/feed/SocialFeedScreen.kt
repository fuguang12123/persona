package com.example.persona.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.persona.data.model.Persona

/**
 * 智能体广场主界面
 *
 * @description 展示智能体列表,支持"全部/推荐"双 Tab 切换、分页加载与滚动到底检测。
 * 推荐页通过缓存优化切换体验,全部页实现无限滚动加载。点击卡片可查看智能体详情。
 *
 * @param onPersonaClick 点击智能体卡片的回调,传入智能体 ID
 * @param onCreateClick 点击创建按钮的回调,跳转到创建智能体页面
 * @param viewModel 智能体广场的 ViewModel,管理数据加载与分页
 *
 * @author Persona Team <persona@project.local>
 * @version v1.0.0
 * @since 2025-11-30
 * @see SocialFeedViewModel 智能体广场数据管理
 * @关联功能 REQ-B3 社交广场
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialFeedScreen(
    onPersonaClick: (Long) -> Unit,
    onCreateClick: () -> Unit,
    viewModel: SocialFeedViewModel = hiltViewModel()
) {
    // 列表滚动状态,用于监听滚动位置
    val listState = rememberLazyListState()

    // 监听是否滚动到底部 (触发下一页加载)
    val isScrollToEnd by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false

            // 当最后可见项索引 >= 总数 - 2 时,认为接近底部
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= totalItems - 2
        }
    }

    // 滚动到底部时触发加载下一页
    LaunchedEffect(isScrollToEnd) {
        if (isScrollToEnd) {
            viewModel.loadNextPage()
        }
    }

    Scaffold(
        topBar = {
            Column {
                // 顶部标题栏
                TopAppBar(title = { Text("智能体广场") })

                // Tab 切换栏 (全部 / 推荐)
                TabRow(selectedTabIndex = viewModel.currentTab) {
                    Tab(
                        selected = viewModel.currentTab == 0,
                        onClick = { viewModel.switchTab(0) }, // 切换到"全部"
                        text = { Text("全部") }
                    )
                    Tab(
                        selected = viewModel.currentTab == 1,
                        onClick = { viewModel.switchTab(1) }, // 切换到"推荐"
                        text = { Text("推荐") }
                    )
                }
            }
        },
        floatingActionButton = {
            // 右下角的创建按钮
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Default.Add, "Create")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 遍历智能体列表,根据是否有推荐分数选择不同卡片样式
                items(viewModel.feedList) { p ->
                    if (p.matchScore != null && p.matchScore > 0) {
                        // 推荐卡片 (带匹配度分数)
                        RecommendationCard(p) { onPersonaClick(p.id) }
                    } else {
                        // 普通卡片
                        PersonaCard(p) { onPersonaClick(p.id) }
                    }
                }

                // 分页加载指示器 (仅在列表有内容且正在加载下一页时显示)
                if (viewModel.isLoading && viewModel.feedList.isNotEmpty() && viewModel.currentPage > 1) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                // 到底提示 (全部页数据加载完毕时显示)
                if (viewModel.isEndReached && viewModel.currentTab == 0 && viewModel.feedList.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "没有更多内容了",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 全屏加载动画 (列表为空或首次加载时显示)
            if (viewModel.isLoading && (viewModel.feedList.isEmpty() || viewModel.currentPage == 1)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        .clickable(enabled = false) {}, // 拦截点击事件
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/**
 * 推荐智能体卡片组件
 *
 * @description 展示带匹配度分数的智能体卡片,包含头像、昵称、标签、匹配度和推荐理由。
 * 突出显示 AI 推荐理由,帮助用户理解推荐逻辑。
 *
 * @param persona 智能体数据对象
 * @param onClick 点击卡片的回调
 *
 * @author Persona Team <persona@project.local>
 * @version v1.0.0
 * @since 2025-11-30
 */
@Composable
fun RecommendationCard(persona: Persona, onClick: () -> Unit) {
    // 统一头像 URL 处理逻辑
    val url = remember(persona.avatarUrl, persona.name) {
        if (persona.avatarUrl.isNullOrBlank()) {
            "https://api.dicebear.com/7.x/avataaars/png?seed=${persona.name}"
        } else {
            persona.avatarUrl.replace("/svg", "/png")
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(4.dp), // 阴影深度
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // 智能体头像
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))

                // 智能体信息 (昵称 + 标签)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = persona.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))

                    // 标签列表 (最多显示 3 个)
                    if (persona.tagsList.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            persona.tagsList.take(3).forEach { tag ->
                                TagChip(text = tag)
                            }
                        }
                    }
                }

                // 匹配度分数显示
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${persona.matchScore}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "匹配度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // 推荐理由容器
            if (!persona.reason.isNullOrEmpty()) {
                ContainerReason(persona.reason)
            }
        }
    }
}

/**
 * 推荐理由容器组件
 *
 * @description 展示 AI 推荐理由的容器,使用淡色背景和星标图标突出显示
 *
 * @param reason 推荐理由文本
 */
@Composable
fun ContainerReason(reason: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 星标图标
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "AI",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(Modifier.width(6.dp))
        // 推荐理由文本
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp
        )
    }
}

/**
 * 普通智能体卡片组件
 *
 * @description 展示不带匹配度的普通智能体卡片,包含头像、昵称、标签和简介
 *
 * @param persona 智能体数据对象
 * @param onClick 点击卡片的回调
 */
@Composable
fun PersonaCard(persona: Persona, onClick: () -> Unit) {
    // 统一头像 URL 处理逻辑
    val url = remember(persona.avatarUrl, persona.name) {
        if (persona.avatarUrl.isNullOrBlank()) {
            "https://api.dicebear.com/7.x/avataaars/png?seed=${persona.name}"
        } else {
            persona.avatarUrl.replace("/svg", "/png")
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F8FA))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 智能体头像
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(16.dp))

            // 智能体信息列
            Column(modifier = Modifier.weight(1f)) {
                // 昵称
                Text(
                    text = persona.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // 标签列表 (最多显示 3 个)
                if (persona.tagsList.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        persona.tagsList.take(3).forEach { tag ->
                            TagChip(text = tag)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 简介 (为空时显示默认文案)
                val descText = if (persona.description.isNullOrBlank())
                    "这个智能体很神秘,还没有介绍..."
                else
                    persona.description

                Text(
                    text = descText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 标签组件
 *
 * @description 展示标签的色块样式组件,使用浅色背景和圆角设计
 *
 * @param text 标签文本
 */
@Composable
fun TagChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
