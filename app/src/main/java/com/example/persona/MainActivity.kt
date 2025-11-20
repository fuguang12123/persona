package com.example.persona

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.persona.ui.chat.ChatScreen // 确保导入了这个
import com.example.persona.ui.create.CreatePersonaScreen
import com.example.persona.ui.feed.SocialFeedScreen
import com.example.persona.ui.login.LoginScreen
import com.example.persona.ui.theme.PersonaTheme
import dagger.hilt.android.AndroidEntryPoint

// 定义路由路径
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object SocialFeed : Screen("feed")
    object CreatePersona : Screen("create_persona")
    object Chat : Screen("chat/{personaId}") {
        // 辅助方法：生成带参数的路由字符串
        fun createRoute(id: Long) = "chat/$id"
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PersonaTheme {
                AppNavHost()
            }
        }
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Login.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 1. 登录页
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.SocialFeed.route) {
                            // 登录成功后，清除登录页之前的所有栈，防止按返回键回到登录页
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            // 2. 广场页
            composable(Screen.SocialFeed.route) {
                SocialFeedScreen(
                    onPersonaClick = { personaId ->
                        // ✅ [修正点 2] 取消注释，启用跳转
                        navController.navigate(Screen.Chat.createRoute(personaId))
                    },
                    onCreateClick = {
                        // 跳转到创建页
                        navController.navigate(Screen.CreatePersona.route)
                    }
                )
            }

            // 3. 创建页
            composable(Screen.CreatePersona.route) {
                CreatePersonaScreen(
                    onBack = {
                        // 保存成功或点击返回，回到广场
                        navController.popBackStack()
                    }
                )
            }

            // 4. 聊天页 (需要接收参数)
            // ✅ [修正点 3] 取消注释，注册路由
            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("personaId") { type = NavType.LongType })
            ) { backStackEntry ->
                // 从路由中解析 personaId
                val id = backStackEntry.arguments?.getLong("personaId") ?: 0L

                ChatScreen(
                    personaId = id,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}