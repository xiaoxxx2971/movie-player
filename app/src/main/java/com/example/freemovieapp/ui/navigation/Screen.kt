package com.example.freemovieapp.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Detail : Screen("detail/{movieId}") {
        fun createRoute(movieId: String) = "detail/$movieId"
    }
    object Player : Screen("player/{movieId}") {
        fun createRoute(movieId: String) = "player/$movieId"
    }
}
