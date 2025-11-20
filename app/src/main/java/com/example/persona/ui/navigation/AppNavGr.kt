package com.example.persona.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
        // 1. 登录页路由
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    // 登录成功后，跳转到广场，并把登录页从后退栈中弹出（防止用户按返回键回到登录页）
                    navController.navigate(Screen.SocialFeed.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // 2. 广场页路由
        composable(Screen.SocialFeed.route) {
            SocialFeedScreen()
        }

        // 后续会在这里添加 Chat 和 Creation 页面
    }
}