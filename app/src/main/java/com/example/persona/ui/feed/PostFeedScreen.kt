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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.persona.data.remote.PostDto

/**
 * 动态广场主界面
 *
 * @description 展示用户发布的动态内容,支持瀑布流布局、点赞/收藏交互,以及"全部/关注"双 Tab 切换。
 * 通过 ViewModel 观察数据流,实现乐观更新与状态同步,对应《最终作业.md》的社交广场浏览与互动功能。
 *
 * @param viewModel 动态信息流的 ViewModel,管理数据加载与用户交互
 * @param onPostClick 点击动态卡片的回调,传入动态 ID
 * @param onCreatePostClick 点击发布按钮的回调,跳转到创建动态页面
 * @param onPersonaClick 点击智能体头像的回调,传入智能体 ID
 *
 * @author Persona Team <persona@project.local>
 * @version v1.0.0
 * @since 2025-11-30
 * @see PostFeedViewModel 动态数据管理
 * @see PostCard 单个动态卡片组件
 * @关联功能 REQ-B3 社交广场
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostFeedScreen(
    viewModel: PostFeedViewModel = hiltViewModel(),
    onPostClick: (Long) -> Unit,
    onCreatePostClick: () -> Unit,
    onPersonaClick: (Long) -> Unit
) {
    // 订阅 ViewModel 的状态流
    val feedState by viewModel.feedState.collectAsState() // 动态列表数据
    val isRefreshing by viewModel.isRefreshing.collectAsState() // 是否正在刷新
    val currentTab by viewModel.currentTab.collectAsState() // 当前选中的 Tab (0=全部, 1=关注)

    Scaffold(
        topBar = {
            Column {
                // 顶部标题栏
                TopAppBar(title = { Text("动态广场") })

                // Tab 切换栏 (全部 / 关注)
                TabRow(selectedTabIndex = currentTab) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { viewModel.switchTab(0) }, // 切换到"全部"
                        text = { Text("全部") }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = { viewModel.switchTab(1) }, // 切换到"关注"
                        text = { Text("关注") }
                    )
                }
            }
        },
        floatingActionButton = {
            // 右下角的发布按钮
            FloatingActionButton(
                onClick = onCreatePostClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "发布")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5)) // 浅灰色背景
        ) {
            // 空状态提示 (当列表为空且未在刷新时显示)
            if (feedState.isEmpty() && !isRefreshing) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("这里静悄悄的...", color = Color.Gray)
                    Button(
                        onClick = { viewModel.refresh() }, // 点击刷新按钮重新加载
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("刷新")
                    }
                }
            } else {
                // 瀑布流布局展示动态列表
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2), // 固定 2 列
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), // 列间距
                    verticalItemSpacing = 8.dp, // 行间距
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 遍历动态列表,为每个动态创建卡片
                    items(feedState, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onLikeClick = { viewModel.toggleLike(post) }, // 点赞/取消点赞
                            onBookmarkClick = { viewModel.toggleBookmark(post) }, // 收藏/取消收藏
                            onClick = { onPostClick(post.id) }, // 点击卡片查看详情
                            onPersonaClick = onPersonaClick // 点击头像跳转到智能体页面
                        )
                    }
                    // 底部留白,避免被 FAB 遮挡
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }

            // 刷新加载指示器 (显示在顶部中央)
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )
            }
        }
    }
}

/**
 * 单个动态卡片组件
 *
 * @description 展示动态内容的卡片,包括图片、文字、作者信息、点赞数和收藏按钮。
 * 支持点击卡片查看详情、点击头像跳转智能体页面、点赞/收藏交互等功能。
 *
 * @param post 动态数据对象,包含内容、图片、作者、点赞数等信息
 * @param onLikeClick 点击点赞按钮的回调
 * @param onBookmarkClick 点击收藏按钮的回调
 * @param onClick 点击卡片的回调
 * @param onPersonaClick 点击作者头像的回调
 *
 * @author Persona Team <persona@project.local>
 * @version v1.0.0
 * @since 2025-11-30
 */
@Composable
fun PostCard(
    post: PostDto,
    onLikeClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onClick: () -> Unit,
    onPersonaClick: (Long) -> Unit
) {
    val context = LocalContext.current

    // 头像 URL 处理逻辑:如果为空则使用默认头像生成服务
    val avatarUrl = remember(post.authorAvatar, post.authorName) {
        if (post.authorAvatar.isNullOrBlank()) {
            "https://api.dicebear.com/7.x/avataaars/png?seed=${post.authorName}"
        } else {
            post.authorAvatar.replace("/svg", "/png") // 统一使用 PNG 格式
        }
    }

    Card(
        shape = RoundedCornerShape(8.dp), // 圆角卡片
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick) // 点击卡片查看详情
    ) {
        Column {
            // 如果有图片,显示第一张图片
            if (!post.imageUrls.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(post.imageUrls.first()) // 取第一张图片
                        .crossfade(true) // 开启淡入动画
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop, // 裁剪填充
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp) // 限制最大高度
                )
            }

            Column(Modifier.padding(10.dp)) {
                // 动态文字内容 (最多显示 4 行,超出部分显示省略号)
                if (post.content.isNotBlank()) {
                    Text(
                        text = post.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // 作者信息行 (头像 + 昵称)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        // 点击头像跳转到智能体详情页
                        post.personaId.toLongOrNull()?.let {
                            if (it > 0) onPersonaClick(it)
                        }
                    }
                ) {
                    // 作者头像
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape), // 圆形裁剪
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(6.dp))

                    // 作者昵称
                    Text(
                        text = post.authorName ?: "Unknown",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 底部交互区 (点赞按钮 + 收藏按钮)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 点赞按钮 (显示点赞数和图标)
                    Row(
                        modifier = Modifier.clickable(onClick = onLikeClick),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (post.isLiked) Color(0xFFFF4D4F) else Color.Gray, // 已点赞显示红色
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (post.likes > 0) "${post.likes}" else "赞", // 点赞数大于 0 时显示数字
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    // 收藏按钮
                    IconButton(
                        onClick = onBookmarkClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (post.isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (post.isBookmarked) Color(0xFFFFC107) else Color.Gray // 已收藏显示黄色
                        )
                    }
                }
            }
        }
    }
}
