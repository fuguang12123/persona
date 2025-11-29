package com.example.persona.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.persona.data.remote.CommentDto

// 注意：DateUtils 需自行保留或使用 Java SimpleDateFormat
// import com.example.persona.utils.DateUtils

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: Long,
    onBack: () -> Unit,
    onPersonaClick: (Long) -> Unit,
    viewModel: PostDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(postId) {
        viewModel.loadPost(postId)
    }

    val uiState by viewModel.uiState.collectAsState()
    var commentText by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var showFullGallery by remember { mutableStateOf(false) }
    var initialPreviewPage by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    if (showFullGallery) {
        val images = uiState.post?.imageUrls ?: emptyList()
        if (images.isNotEmpty()) {
            FullScreenImageGallery(images, initialPreviewPage) {
                showFullGallery = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            uiState.post?.personaId?.toLongOrNull()?.let {
                                if (it > 0) onPersonaClick(it)
                            }
                        }
                    ) {
                        // [Logic Update] 顶部栏头像逻辑
                        val rawAvatar = uiState.authorAvatar ?: uiState.post?.authorAvatar
                        val authorName = uiState.authorName ?: "unknown"
                        val finalAvatarUrl = remember(rawAvatar, authorName) {
                            if (rawAvatar.isNullOrBlank()) {
                                "https://api.dicebear.com/7.x/avataaars/png?seed=$authorName"
                            } else {
                                rawAvatar.replace("/svg", "/png")
                            }
                        }

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(finalAvatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray),
                            contentScale = ContentScale.Crop,
                            placeholder = rememberVectorPainter(Icons.Default.Person),
                            error = rememberVectorPainter(Icons.Default.Person)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = uiState.authorName ?: "详情",
                                style = MaterialTheme.typography.titleMedium
                            )
                            uiState.post?.userId?.let { userId ->
                                if (userId > 0L) {
                                    Text(
                                        text = "Created by User $userId",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // [New] 动态详情页的关注按钮
                    TextButton(onClick = { viewModel.toggleFollowAuthor() }) {
                        Text(if (uiState.isAuthorFollowed) "已关注" else "关注")
                    }
                }
            )
        },
        bottomBar = {
            // 底部评论输入框
            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 2.dp
            ) {
                Column {
                    if (replyTo != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF0F0F0))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "回复 ${replyTo!!.second}:",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            TextButton(
                                onClick = { replyTo = null },
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("取消", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            placeholder = {
                                Text(if (replyTo != null) "回复..." else "说点什么...")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 3
                        )

                        Spacer(Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (commentText.isNotBlank()) {
                                    viewModel.sendComment(postId, commentText, replyTo?.first)
                                    commentText = ""
                                    replyTo = null
                                }
                            },
                            enabled = commentText.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (commentText.isBlank()) {
                            IconButton(onClick = { viewModel.toggleBookmark(postId) }) {
                                Icon(
                                    imageVector = if (uiState.isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Mark",
                                    tint = if (uiState.isBookmarked) Color(0xFFFFC107) else Color.Gray
                                )
                            }
                            IconButton(onClick = { viewModel.toggleLike(postId) }) {
                                Icon(
                                    imageVector = if (uiState.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (uiState.isLiked) Color.Red else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.post == null && uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                item {
                    val images = uiState.post?.imageUrls ?: emptyList()
                    if (images.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                        ) {
                            val pagerState = rememberPagerState(pageCount = { images.size })
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(images[page])
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            initialPreviewPage = page
                                            showFullGallery = true
                                        }
                                )
                            }
                            // 指示器省略...
                        }
                    }
                }

                item {
                    Column(Modifier.padding(16.dp)) {
                        if (uiState.likeCount > 0) {
                            Text(
                                text = "${uiState.likeCount} 次赞",
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            text = uiState.post?.content ?: "",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = Color.LightGray
                        )
                        Spacer(Modifier.height(8.dp))
                        // 评论统计
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "共 ${uiState.commentGroups.sumOf { 1 + it.replies.size }} 条评论",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }

                items(uiState.commentGroups) { group ->
                    CommentGroupItem(group) { id, name -> replyTo = id to name }
                }

                item { Spacer(Modifier.height(50.dp)) }
            }
        }
    }
}

// 辅助组件：评论组
@Composable
fun CommentGroupItem(group: CommentGroup, onReplyClick: (Long, String) -> Unit) {
    // [New Logic] 展开/收起状态
    var isExpanded by remember { mutableStateOf(false) }
    val replyCount = group.replies.size
    val previewCount = 2 // 默认展示2条

    Column {
        SingleCommentRow(group.root, false, onReplyClick)

        if (replyCount > 0) {
            Column(Modifier.padding(start = 32.dp)) {
                // 如果未展开，只显示 previewCount 条；否则显示全部
                val displayReplies = if (isExpanded) group.replies else group.replies.take(previewCount)

                displayReplies.forEach {
                    SingleCommentRow(it, true, onReplyClick)
                }

                // 按钮显示逻辑：只有回复数超过预览数时才显示
                if (replyCount > previewCount) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        // 去除默认Padding以便左对齐
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "收起" else "展开 ${replyCount - previewCount} 条回复",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = Color.LightGray.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun SingleCommentRow(
    comment: CommentDto,
    isReply: Boolean,
    onReplyClick: (Long, String) -> Unit
) {
    val name = comment.userName ?: "User ${comment.userId}"

    // [Logic Update] 评论区头像逻辑
    val avatar = remember(comment.userAvatar, comment.userId) {
        if (comment.userAvatar.isNullOrBlank()) {
            "https://api.dicebear.com/7.x/avataaars/png?seed=${comment.userId}"
        } else {
            comment.userAvatar.replace("/svg", "/png")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onReplyClick(comment.id, name) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatar)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(if (isReply) 24.dp else 36.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenImageGallery(
    images: List<String>,
    initialPage: Int,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val pagerState = rememberPagerState(
                initialPage = initialPage,
                pageCount = { images.size }
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(images[page])
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onDismiss() }
                )
            }
        }
    }
}