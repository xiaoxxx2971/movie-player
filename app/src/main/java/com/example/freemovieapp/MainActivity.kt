package com.example.freemovieapp

import android.net.Uri
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
import com.example.freemovieapp.model.Episode
import com.example.freemovieapp.model.Movie
import com.example.freemovieapp.model.VideoSource
import com.example.freemovieapp.ui.navigation.Screen
import com.example.freemovieapp.ui.screens.DetailScreen
import com.example.freemovieapp.ui.screens.DownloadManagerScreen
import com.example.freemovieapp.ui.screens.HomeScreen
import com.example.freemovieapp.ui.screens.PlayerScreen
import com.example.freemovieapp.ui.theme.DarkBackground
import com.example.freemovieapp.ui.theme.FreeMovieAppTheme
import java.net.URLDecoder

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
                                },
                                onNavigateToDownloads = {
                                    navController.navigate("downloads")
                                }
                            )
                        }

                        composable("downloads") {
                            DownloadManagerScreen(
                                onBackClick = { navController.popBackStack() },
                                onPlayLocalVideo = { localPath: String, title: String ->
                                    val encodedPath = Uri.encode(localPath)
                                    val encodedTitle = Uri.encode(title)
                                    navController.navigate("local_player?path=$encodedPath&title=$encodedTitle")
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
                                onPlayClick = { id, sourceIndex, epIndex ->
                                    navController.navigate("player/$id?sourceIndex=$sourceIndex&epIndex=$epIndex")
                                }
                            )
                        }

                        composable(
                            route = "player/{movieId}?sourceIndex={sourceIndex}&epIndex={epIndex}",
                            arguments = listOf(
                                navArgument("movieId") { type = NavType.StringType },
                                navArgument("sourceIndex") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                },
                                navArgument("epIndex") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                }
                            )
                        ) { backStackEntry ->
                            val movieId = backStackEntry.arguments?.getString("movieId") ?: ""
                            val sourceIndex = backStackEntry.arguments?.getInt("sourceIndex") ?: 0
                            val epIndex = backStackEntry.arguments?.getInt("epIndex") ?: 0
                            PlayerScreen(
                                movieId = movieId,
                                initialSourceIndex = sourceIndex,
                                initialEpisodeIndex = epIndex,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "local_player?path={path}&title={title}",
                            arguments = listOf(
                                navArgument("path") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val rawPath = backStackEntry.arguments?.getString("path") ?: ""
                            val rawTitle = backStackEntry.arguments?.getString("title") ?: ""
                            val decodedPath = URLDecoder.decode(rawPath, "UTF-8")
                            val decodedTitle = URLDecoder.decode(rawTitle, "UTF-8")

                            val localMovie = Movie(
                                id = "local_temp",
                                title = decodedTitle,
                                category = "离线本地视频",
                                year = "本地",
                                rating = 10.0,
                                duration = "本地视频",
                                description = "本地已下载离线视频，零流量极速播放。",
                                posterUrl = "",
                                backdropUrl = "",
                                sources = listOf(
                                    VideoSource("本地文件", listOf(Episode("本地播放", decodedPath)))
                                ),
                                episodes = listOf(Episode("本地播放", decodedPath))
                            )

                            com.example.freemovieapp.data.MovieRepository.getMovieById("local_temp")
                            PlayerScreen(
                                movieId = localMovie.id,
                                initialSourceIndex = 0,
                                initialEpisodeIndex = 0,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
