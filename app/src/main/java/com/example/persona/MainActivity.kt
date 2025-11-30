package com.example.persona

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.persona.data.manager.SessionManager
import com.example.persona.ui.MainViewModel
import com.example.persona.ui.chat.ChatScreen
import com.example.persona.ui.chat.list.ChatListScreen
import com.example.persona.ui.create.CreatePersonaScreen
import com.example.persona.ui.create.CreatePostScreen
import com.example.persona.ui.detail.PersonaDetailScreen
import com.example.persona.ui.detail.PostDetailScreen
import com.example.persona.ui.feed.PostFeedScreen
import com.example.persona.ui.feed.SocialFeedScreen
import com.example.persona.ui.login.LoginScreen
import com.example.persona.ui.login.RegisterScreen
import com.example.persona.ui.notification.NotificationScreen
import com.example.persona.ui.profile.EditProfileScreen
import com.example.persona.ui.profile.ProfileScreen
import com.example.persona.ui.profile.SettingsScreen
import com.example.persona.ui.theme.PersonaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Login : Screen("login", "登录")
    object Register : Screen("register", "注册")
    object ChatList : Screen("chat_list", "对话", Icons.Default.ChatBubble)
    object SocialFeed : Screen("feed", "智能体", Icons.Default.Contacts)
    object CreatePost : Screen("create_post?personaId={personaId}", "发布", Icons.Default.AddCircleOutline) {
        fun createRoute(personaId: Long? = null) = if (personaId != null) "create_post?personaId=$personaId" else "create_post"
    }
    object PostFeed : Screen("post_feed", "动态", Icons.Default.DynamicFeed)
    object Profile : Screen("profile", "我的", Icons.Default.PersonOutline)
    object Settings : Screen("settings", "设置")
    object EditProfile : Screen("edit_profile", "编辑资料")
    object CreatePersona : Screen("create_persona?editId={editId}", "创建智能体") {
        fun createRoute(editId: Long? = null) = if (editId != null) "create_persona?editId=$editId" else "create_persona"
    }
    object Chat : Screen("chat/{personaId}", "聊天") {
        fun createRoute(id: Long) = "chat/$id"
    }
    object PostDetail : Screen("post_detail/{postId}", "动态详情") {
        fun createRoute(id: Long) = "post_detail/$id"
    }
    object PersonaDetail : Screen("persona_detail/{personaId}", "智能体详情") {
        fun createRoute(id: Long) = "persona_detail/$id"
    }
    object Notification : Screen("notifications", "通知")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PersonaTheme {
                // 传入 mainViewModel 以获取 startDestination
                MainAppScreen(sessionManager, mainViewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(sessionManager: SessionManager, mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // ✅ [New] 监听初始路由状态
    val startDestination by mainViewModel.startDestination.collectAsState()

    // 监听踢下线事件
    LaunchedEffect(Unit) {
        sessionManager.logoutEvent.collect {
            Toast.makeText(context, "登录已过期，请重新登录", Toast.LENGTH_SHORT).show()
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // ✅ [New] 在决定好去哪之前，显示 Loading 页 (防止闪屏)
    if (startDestination == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        // startDestination 确定后，渲染主界面
        val bottomNavItems = listOf(Screen.ChatList, Screen.SocialFeed, Screen.CreatePost, Screen.PostFeed, Screen.Profile)
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val currentRoute = currentDestination?.route

        val showBottomBar = currentRoute in listOf(
            Screen.ChatList.route, Screen.SocialFeed.route, Screen.CreatePost.route, Screen.PostFeed.route, Screen.Profile.route
        ) || currentRoute?.startsWith("create_post") == true

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        bottomNavItems.forEach { screen ->
                            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                icon = { Icon(screen.icon!!, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                selected = isSelected,
                                onClick = {
                                    val targetRoute = if (screen == Screen.CreatePost) {
                                        Screen.CreatePost.createRoute()
                                    } else {
                                        screen.route
                                    }

                                    navController.navigate(targetRoute) {
                                        // ✅ [Core Fix] 实现点击 Tab 总是重置到初始界面

                                        // 1. 弹出到起始页（清除栈）：保证栈的层级不会无限堆叠
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            // 2. saveState = false (关键点)：
                                            // 离开当前 Tab 时，直接销毁它的状态和子页面（例如 ChatDetail）。
                                            // 这样下次回来时，它是全新的。
                                            saveState = false
                                        }

                                        // 3. 避免同一个页面被多次压入栈顶
                                        launchSingleTop = true

                                        // 4. restoreState = false (关键点)：
                                        // 进入目标 Tab 时，不要尝试恢复之前的状态。
                                        // 强制重新加载该 Tab 的初始页面。
                                        restoreState = false
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = if (screen == Screen.CreatePost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination!!,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Login.route) {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(Screen.ChatList.route) { popUpTo(Screen.Login.route) { inclusive = true } }
                        },
                        onRegisterClick = { navController.navigate(Screen.Register.route) }
                    )
                }
                composable(Screen.Register.route) {
                    RegisterScreen(
                        onBackClick = { navController.popBackStack() },
                        onRegisterSuccess = {
                            navController.navigate(Screen.ChatList.route) { popUpTo(Screen.Login.route) { inclusive = true } }
                        }
                    )
                }

                composable(Screen.ChatList.route) {
                    ChatListScreen(
                        onChatClick = { pid ->
                            navController.navigate(Screen.Chat.createRoute(pid))
                        },
                        onNotificationClick = { navController.navigate(Screen.Notification.route) }
                    )
                }

                composable(Screen.Notification.route) {
                    NotificationScreen(
                        onBackClick = { navController.popBackStack() },
                        onPostClick = { pid -> navController.navigate(Screen.PostDetail.createRoute(pid)) }
                    )
                }
                composable(Screen.SocialFeed.route) {
                    SocialFeedScreen(
                        onPersonaClick = { pid -> navController.navigate(Screen.PersonaDetail.createRoute(pid)) },
                        onCreateClick = { navController.navigate(Screen.CreatePersona.createRoute()) }
                    )
                }
                composable(route = Screen.CreatePost.route, arguments = listOf(navArgument("personaId") { type = NavType.LongType; defaultValue = -1L })) {
                    CreatePostScreen(
                        onBackClick = { navController.popBackStack() },
                        onPostSuccess = { navController.navigate(Screen.PostFeed.route) { popUpTo(Screen.PostFeed.route) { inclusive = true } } }
                    )
                }
                composable(Screen.PostFeed.route) {
                    PostFeedScreen(
                        onPostClick = { pid -> navController.navigate(Screen.PostDetail.createRoute(pid)) },
                        onCreatePostClick = { navController.navigate(Screen.CreatePost.route) },
                        onPersonaClick = { pid -> navController.navigate(Screen.PersonaDetail.createRoute(pid)) }
                    )
                }
                composable(Screen.Profile.route) {
                    ProfileScreen(
                        onSettingsClick = { navController.navigate(Screen.Settings.route) },
                        onPostClick = { pid -> navController.navigate(Screen.PostDetail.createRoute(pid)) },
                        onPersonaClick = { pid -> navController.navigate(Screen.PersonaDetail.createRoute(pid)) }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onLogout = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onEditProfileClick = { navController.navigate(Screen.EditProfile.route) }
                    )
                }

                composable(Screen.EditProfile.route) {
                    EditProfileScreen(onBack = { navController.popBackStack() })
                }

                composable(route = Screen.CreatePersona.route, arguments = listOf(navArgument("editId") { type = NavType.LongType; defaultValue = -1L })) {
                    CreatePersonaScreen(onBack = { navController.popBackStack() })
                }

                composable(route = Screen.Chat.route, arguments = listOf(navArgument("personaId") { type = NavType.LongType })) {
                    val id = it.arguments?.getLong("personaId") ?: 0L
                    ChatScreen(
                        personaId = id,
                        onBack = { navController.popBackStack() },
                        onPersonaDetailClick = { pid -> navController.navigate(Screen.PersonaDetail.createRoute(pid)) }
                    )
                }

                composable(route = Screen.PostDetail.route, arguments = listOf(navArgument("postId") { type = NavType.LongType })) {
                    val id = it.arguments?.getLong("postId") ?: 0L
                    PostDetailScreen(postId = id, onBack = { navController.popBackStack() }, onPersonaClick = { pid -> navController.navigate(Screen.PersonaDetail.createRoute(pid)) })
                }
                composable(route = Screen.PersonaDetail.route, arguments = listOf(navArgument("personaId") { type = NavType.LongType })) {
                    val id = it.arguments?.getLong("personaId") ?: 0L
                    PersonaDetailScreen(
                        personaId = id,
                        onBackClick = { navController.popBackStack() },
                        onChatClick = { pid -> navController.navigate(Screen.Chat.createRoute(pid)) },
                        onCreatePostClick = { pid -> navController.navigate(Screen.CreatePost.createRoute(pid)) },
                        onEditClick = { pid -> navController.navigate(Screen.CreatePersona.createRoute(editId = pid)) }
                    )
                }
            }
        }
    }
}