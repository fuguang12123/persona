package com.example.persona.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object SocialFeed : Screen("social_feed")
    object Creation : Screen("creation")
    object Chat : Screen("chat/{personaId}") {
        fun createRoute(personaId: String) = "chat/$personaId"
    }
}