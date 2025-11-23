package com.example.persona.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.persona.Screen // 引用 MainActivity 中定义的 Screen
import com.example.persona.ui.feed.PostFeedScreen
import com.example.persona.ui.login.LoginScreen
import com.example.persona.ui.screens.SocialFeedScreen

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // 1. 登录页
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    // Day 7 测试路由: 跳转到新的动态广场 Screen.PostFeed.route
                    navController.navigate(Screen.PostFeed.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // 2. 广场页 (旧列表)
        composable(Screen.SocialFeed.route) {
            SocialFeedScreen()
        }

        // 3. 动态广场 (新瀑布流)
        composable(Screen.PostFeed.route) {
            PostFeedScreen(
                onPostClick = { /* TODO */ },
                onCreatePostClick = { /* TODO */ }
            )
        }

        // ... 其他页面
    }
}