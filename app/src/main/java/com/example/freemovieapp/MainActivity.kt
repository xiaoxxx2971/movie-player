package com.example.freemovieapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.freemovieapp.ui.navigation.Screen
import com.example.freemovieapp.ui.screens.DetailScreen
import com.example.freemovieapp.ui.screens.HomeScreen
import com.example.freemovieapp.ui.screens.PlayerScreen
import com.example.freemovieapp.ui.theme.DarkBackground
import com.example.freemovieapp.ui.theme.FreeMovieAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FreeMovieAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onMovieClick = { movieId ->
                                    navController.navigate(Screen.Detail.createRoute(movieId))
                                }
                            )
                        }

                        composable(
                            route = Screen.Detail.route,
                            arguments = listOf(navArgument("movieId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val movieId = backStackEntry.arguments?.getString("movieId") ?: ""
                            DetailScreen(
                                movieId = movieId,
                                onBackClick = { navController.popBackStack() },
                                onPlayClick = { id, epIndex ->
                                    navController.navigate("player/$id?epIndex=$epIndex")
                                }
                            )
                        }

                        composable(
                            route = "player/{movieId}?epIndex={epIndex}",
                            arguments = listOf(
                                navArgument("movieId") { type = NavType.StringType },
                                navArgument("epIndex") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                }
                            )
                        ) { backStackEntry ->
                            val movieId = backStackEntry.arguments?.getString("movieId") ?: ""
                            val epIndex = backStackEntry.arguments?.getInt("epIndex") ?: 0
                            PlayerScreen(
                                movieId = movieId,
                                initialEpisodeIndex = epIndex,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
