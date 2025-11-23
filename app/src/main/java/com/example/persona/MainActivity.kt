package com.example.persona

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.persona.ui.chat.ChatScreen
import com.example.persona.ui.chat.list.ChatListScreen // [New]
import com.example.persona.ui.create.CreatePersonaScreen
import com.example.persona.ui.create.CreatePostScreen
import com.example.persona.ui.feed.PostFeedScreen
import com.example.persona.ui.feed.SocialFeedScreen
import com.example.persona.ui.login.LoginScreen
import com.example.persona.ui.profile.ProfileScreen
import com.example.persona.ui.theme.PersonaTheme
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Login : Screen("login", "登录")

    // 底部导航 Tab (调整顺序)
    object ChatList : Screen("chat_list", "对话", Icons.Default.ChatBubble) // [New]
    object SocialFeed : Screen("feed", "智能体", Icons.Default.Contacts)
    object CreatePost : Screen("create_post", "发布", Icons.Default.AddCircleOutline)
    object PostFeed : Screen("post_feed", "动态", Icons.Default.DynamicFeed)
    object Profile : Screen("profile", "我的", Icons.Default.PersonOutline)

    // 详情页
    object CreatePersona : Screen("create_persona", "创建智能体")
    object Chat : Screen("chat/{personaId}", "聊天") {
        fun createRoute(id: Long) = "chat/$id"
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PersonaTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    val navController = rememberNavController()

    // 底部导航项 (5个)
    val bottomNavItems = listOf(
        Screen.ChatList,   // 1. 对话 (历史记录)
        Screen.SocialFeed, // 2. 智能体 (通讯录)
        Screen.CreatePost, // 3. 发布 (中间)
        Screen.PostFeed,   // 4. 动态 (广场)
        Screen.Profile     // 5. 我的
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

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
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
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
            startDestination = Screen.Login.route, // 初始页
            modifier = Modifier.padding(innerPadding)
        ) {
            // 登录
            composable(Screen.Login.route) {
                LoginScreen(onLoginSuccess = {
                    navController.navigate(Screen.ChatList.route) { // 登录后去对话列表
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                })
            }

            // Tab 1: 对话列表
            composable(Screen.ChatList.route) {
                ChatListScreen(
                    onChatClick = { personaId ->
                        navController.navigate(Screen.Chat.createRoute(personaId))
                    }
                )
            }

            // Tab 2: 智能体 (通讯录)
            composable(Screen.SocialFeed.route) {
                SocialFeedScreen(
                    onPersonaClick = { personaId ->
                        navController.navigate(Screen.Chat.createRoute(personaId))
                    },
                    onCreateClick = {
                        navController.navigate(Screen.CreatePersona.route)
                    }
                )
            }

            // Tab 3: 发布动态
            composable(Screen.CreatePost.route) {
                CreatePostScreen(
                    personaId = 1L,
                    onBackClick = { navController.popBackStack() },
                    onPostSuccess = {
                        navController.navigate(Screen.PostFeed.route) {
                            popUpTo(Screen.PostFeed.route) { inclusive = true }
                        }
                    }
                )
            }

            // Tab 4: 动态广场
            composable(Screen.PostFeed.route) {
                PostFeedScreen(
                    onPostClick = { /* TODO */ },
                    onCreatePostClick = { navController.navigate(Screen.CreatePost.route) }
                )
            }

            // Tab 5: 我的
            composable(Screen.Profile.route) { ProfileScreen() }

            // --- 详情页 ---
            composable(Screen.CreatePersona.route) {
                CreatePersonaScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("personaId") { type = NavType.LongType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("personaId") ?: 0L
                ChatScreen(personaId = id, onBack = { navController.popBackStack() })
            }
        }
    }
}