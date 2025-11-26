package com.example.persona.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.persona.data.remote.PostDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit,
    onPostClick: (Long) -> Unit,
    onPersonaClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.user

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人中心") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // --- 头部个人信息 ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                // 背景图
                AsyncImage(
                    model = user?.backgroundImageUrl ?: "https://picsum.photos/800/400",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // 头像与昵称
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = user?.avatarUrl ?: "",
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                            .background(Color.Gray)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = user?.nickname ?: user?.username ?: "加载中...",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            text = "ID: ${user?.id}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // --- Tabs 选项卡 ---
            TabRow(selectedTabIndex = uiState.activeTab) {
                Tab(selected = uiState.activeTab == 0, onClick = { viewModel.switchTab(0) }, icon = { Icon(Icons.Default.GridOn, null) }, text = { Text("动态") })
                Tab(selected = uiState.activeTab == 1, onClick = { viewModel.switchTab(1) }, icon = { Icon(Icons.Default.Favorite, null) }, text = { Text("点赞") })
                Tab(selected = uiState.activeTab == 2, onClick = { viewModel.switchTab(2) }, icon = { Icon(Icons.Default.Bookmark, null) }, text = { Text("收藏") })
                Tab(selected = uiState.activeTab == 3, onClick = { viewModel.switchTab(3) }, icon = { Icon(Icons.Default.Person, null) }, text = { Text("智能体") })
            }

            // --- 内容列表 ---
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    when (uiState.activeTab) {
                        3 -> {
                            // 智能体列表 (保持原有的网格布局，看起来更整齐)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(uiState.myPersonas) { persona ->
                                    Card(
                                        modifier = Modifier.clickable { onPersonaClick(persona.id) },
                                        elevation = CardDefaults.cardElevation(2.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        Column(Modifier.padding(8.dp)) {
                                            AsyncImage(model = persona.avatarUrl, contentDescription = null, modifier = Modifier.size(50.dp).clip(CircleShape))
                                            Text(persona.name, style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            // 动态/点赞/收藏列表 -> 切换为【三列瀑布流】
                            val posts = when(uiState.activeTab) {
                                0 -> uiState.myPosts
                                1 -> uiState.myLikes
                                2 -> uiState.myBookmarks
                                else -> emptyList()
                            }

                            // 使用 StaggeredGrid 实现瀑布流
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(3), // 3列
                                contentPadding = PaddingValues(4.dp),  // 极小边距
                                horizontalArrangement = Arrangement.spacedBy(4.dp), // ✅ 修正: 使用 horizontalArrangement 替代 horizontalItemSpacing
                                verticalItemSpacing = 4.dp,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(posts) { post ->
                                    WaterfallPostItem(post = post, onClick = { onPostClick(post.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 瀑布流单项卡片
 * - 有图：展示图片 + 底部渐变阴影 + 白色文字
 * - 无图：展示纯文本内容 + 灰色文字
 */
@Composable
fun WaterfallPostItem(post: PostDto, onClick: () -> Unit) {
    val hasImage = !post.imageUrls.isNullOrEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp), // 小圆角
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box {
            if (hasImage) {
                // === 样式 A：有图片 ===
                Box {
                    // 1. 图片 (宽度填满，高度自适应)
                    AsyncImage(
                        model = post.imageUrls[0],
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        contentScale = ContentScale.Crop
                    )

                    // 2. 底部黑色渐变遮罩 (为了让白色文字看清)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                )
                            )
                    )

                    // 3. 点赞数 (左下角，白色)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = post.likes.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            } else {
                // === 样式 B：纯文字 ===
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(
                        text = post.content,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6, // 最多显示6行文字
                        overflow = TextOverflow.Ellipsis,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 点赞数 (灰色)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = post.likes.toString(),
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}