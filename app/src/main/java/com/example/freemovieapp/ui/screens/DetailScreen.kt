package com.example.freemovieapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.freemovieapp.data.MovieRepository
import com.example.freemovieapp.ui.theme.*

@Composable
fun DetailScreen(
    movieId: String,
    onBackClick: () -> Unit,
    onPlayClick: (String, Int) -> Unit
) {
    val movie = MovieRepository.getMovieById(movieId) ?: return
    var selectedEpisodeIndex by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部海报大图
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                AsyncImage(
                    model = movie.backdropUrl,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, DarkBackground),
                                startY = 120f
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = movie.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(DarkSurface, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = movie.rating.toString(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = movie.year, color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "·", color = TextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = movie.category, color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "·", color = TextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = movie.duration, color = TextSecondary, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 立即播放按钮
                val currentEpName = movie.episodes.getOrNull(selectedEpisodeIndex)?.name ?: "正片"
                Button(
                    onClick = { onPlayClick(movie.id, selectedEpisodeIndex) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "播放 $currentEpName (高清原生流)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 选集功能专区
                if (movie.episodes.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "剧集列表",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "共 ${movie.episodes.size} 集",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 剧集选集卡片网格展示
                    val gridHeight = if (movie.episodes.size <= 4) 50.dp else if (movie.episodes.size <= 8) 100.dp else 160.dp
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 75.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridHeight)
                    ) {
                        itemsIndexed(movie.episodes) { index, ep ->
                            val isSelected = (index == selectedEpisodeIndex)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) NetflixRed else DarkSurface)
                                    .clickable {
                                        selectedEpisodeIndex = index
                                        onPlayClick(movie.id, index)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    text = ep.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = "剧情简介",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = movie.description,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .padding(top = 36.dp, start = 16.dp)
                .background(Color(0x66000000), RoundedCornerShape(20.dp))
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }
    }
}
