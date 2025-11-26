package com.example.persona.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
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
import com.example.persona.utils.DateUtils

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: Long,
    onBack: () -> Unit,
    // [New] 跳转详情页回调
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
            FullScreenImageGallery(
                images = images,
                initialPage = initialPreviewPage,
                onDismiss = { showFullGallery = false }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // [Fix] 标题栏区域改为可点击
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val pid = uiState.post?.personaId?.toLongOrNull() ?: 0L
                            if (pid > 0) onPersonaClick(pid)
                        }
                    ) {
                        val rawAvatar = uiState.authorAvatar ?: uiState.post?.authorAvatar
                        val finalAvatarUrl = remember(rawAvatar) {
                            if (rawAvatar.isNullOrBlank()) {
                                "https://api.dicebear.com/7.x/avataaars/png?seed=${uiState.authorName ?: "unknown"}"
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
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.LightGray),
                            contentScale = ContentScale.Crop,
                            placeholder = rememberVectorPainter(Icons.Default.Person),
                            error = rememberVectorPainter(Icons.Default.Person)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(uiState.authorName ?: uiState.post?.authorName ?: "详情", style = MaterialTheme.typography.titleMedium)
                            uiState.post?.userId?.let { userId ->
                                if (userId > 0L) {
                                    Text("Created by User $userId", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, tonalElevation = 2.dp) {
                Column {
                    if (replyTo != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF0F0F0)).padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("回复 ${replyTo!!.second}:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            TextButton(onClick = { replyTo = null }, modifier = Modifier.height(24.dp)) {
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
                            placeholder = { Text(if (replyTo != null) "回复..." else "说点什么...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 3
                        )
                        Spacer(modifier = Modifier.width(8.dp))

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
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
                        }

                        if (commentText.isBlank()) {
                            IconButton(onClick = { viewModel.toggleBookmark(postId) }) {
                                Icon(
                                    imageVector = if (uiState.isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Bookmark",
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                item {
                    val images = uiState.post?.imageUrls ?: emptyList()
                    if (images.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                            val pagerState = rememberPagerState(pageCount = { images.size })
                            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
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
                            if (images.size > 1) {
                                Row(
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    repeat(images.size) { index ->
                                        Box(
                                            modifier = Modifier.weight(1f).height(3.dp)
                                                .background(
                                                    color = if (index <= pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(1.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val likeCount = uiState.likeCount
                        if (likeCount > 0) {
                            Text("$likeCount 次赞", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = uiState.post?.content ?: "",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        uiState.post?.createdAt?.let {
                            if (it > 0) {
                                Text(
                                    text = DateUtils.formatTimestamp(it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val commentCount = uiState.commentGroups.sumOf { 1 + it.replies.size }
                            Text("共 $commentCount 条评论", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Spacer(Modifier.weight(1f))

                            if (uiState.isCommentsLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else if (uiState.error != null) {
                                TextButton(onClick = { viewModel.refreshComments(postId) }) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("点击重试", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                items(uiState.commentGroups) { group ->
                    CommentGroupItem(
                        group = group,
                        onReplyClick = { id, name -> replyTo = id to name }
                    )
                }

                item { Spacer(modifier = Modifier.height(50.dp)) }
            }
        }
    }
}

// ... 下面的 Composable (CommentGroupItem, SingleCommentRow, FullScreenImageGallery) 保持不变，
// 除非你想点击评论里的头像跳转，但考虑到评论可能是普通用户，这里暂不跳转智能体详情。
@Composable
fun CommentGroupItem(
    group: CommentGroup,
    onReplyClick: (Long, String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val replyCount = group.replies.size

    Column {
        SingleCommentRow(
            comment = group.root,
            isReply = false,
            onReplyClick = onReplyClick
        )

        if (replyCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp, bottom = 4.dp)
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(1.dp)
                        .padding(end = 4.dp)
                        .background(Color.LightGray.copy(alpha = 0.5f))
                )

                Text(
                    text = if (isExpanded) "收起" else "展开 $replyCount 条回复",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                group.replies.forEach { reply ->
                    SingleCommentRow(
                        comment = reply,
                        isReply = true,
                        onReplyClick = onReplyClick
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp), thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.3f))
    }
}

@Composable
fun SingleCommentRow(
    comment: CommentDto,
    isReply: Boolean,
    onReplyClick: (Long, String) -> Unit
) {
    val displayName = comment.userName ?: "User ${comment.userId}"
    val rawAvatar = comment.userAvatar
    val displayAvatar = remember(rawAvatar) {
        if (rawAvatar.isNullOrBlank()) {
            "https://api.dicebear.com/7.x/avataaars/png?seed=${comment.userId}"
        } else {
            rawAvatar.replace("/svg", "/png")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onReplyClick(comment.id, displayName) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(start = if (isReply) 32.dp else 0.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(displayAvatar)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(if (isReply) 24.dp else 36.dp)
                .clip(CircleShape)
                .background(Color.LightGray),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(Icons.Default.Person),
            error = rememberVectorPainter(Icons.Default.Person)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
                Text(
                    text = DateUtils.formatTimeAgo(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(text = comment.content, style = MaterialTheme.typography.bodyMedium)
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
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { images.size })
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(images[page])
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clickable { onDismiss() }
                )
            }
            if (images.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 16.dp)
            ) {
                Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}