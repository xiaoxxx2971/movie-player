package com.example.freemovieapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.freemovieapp.data.DownloadHelper
import com.example.freemovieapp.data.DownloadedVideoItem
import com.example.freemovieapp.data.MovieRepository
import com.example.freemovieapp.model.Movie
import com.example.freemovieapp.ui.components.MovieCard
import com.example.freemovieapp.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMovieClick: (String) -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchQuery by remember { mutableStateOf(MovieRepository.lastSearchQuery) }
    var selectedCategory by remember { mutableStateOf("全部") }
    var isLoading by remember { mutableStateOf(MovieRepository.cachedHomeMovies.isEmpty() && MovieRepository.cachedSearchResults.isEmpty()) }
    var movieList by remember {
        mutableStateOf(
            if (MovieRepository.lastSearchQuery.isNotBlank() && MovieRepository.cachedSearchResults.isNotEmpty()) {
                MovieRepository.cachedSearchResults
            } else {
                MovieRepository.cachedHomeMovies
            }
        )
    }

    val categories = remember(movieList) {
        listOf("全部") + movieList.map { it.category }.distinct()
    }

    val displayedMovies = remember(movieList, selectedCategory) {
        if (selectedCategory == "全部") {
            movieList
        } else {
            movieList.filter { it.category == selectedCategory }
        }
    }

    LaunchedEffect(Unit) {
        if (movieList.isEmpty()) {
            isLoading = true
            movieList = MovieRepository.fetchLatestMovies()
            isLoading = false
        }
    }

    fun executeSearch() {
        keyboardController?.hide()
        coroutineScope.launch {
            isLoading = true
            selectedCategory = "全部"
            movieList = MovieRepository.searchOnlineMovies(searchQuery)
            isLoading = false
        }
    }

    fun forceRefresh() {
        coroutineScope.launch {
            isLoading = true
            searchQuery = ""
            selectedCategory = "全部"
            MovieRepository.lastSearchQuery = ""
            MovieRepository.cachedSearchResults = emptyList()
            movieList = MovieRepository.fetchLatestMovies(forceRefresh = true)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "极光影院",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NetflixRed
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "1080P 超清",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier
                                .background(AccentGold, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToDownloads) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "离线管理",
                            tint = AccentGold
                        )
                    }

                    IconButton(onClick = { forceRefresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索电影、电视剧全网 1080P...", color = TextSecondary, fontSize = 13.sp) },
                leadingIcon = {
                    IconButton(onClick = { executeSearch() }) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "搜索", tint = NetflixRed)
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            executeSearch()
                        }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "清空", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { executeSearch() }),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NetflixRed,
                    unfocusedBorderColor = DarkSurfaceVariant,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NetflixRed)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "正在极速载入影视库...", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else if (movieList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "未搜索到相关影片，换个关键词试试吧",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                val bannerMovie = movieList.firstOrNull()
                if (searchQuery.isBlank() && bannerMovie != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onMovieClick(bannerMovie.id) }
                    ) {
                        AsyncImage(
                            model = bannerMovie.posterUrl,
                            contentDescription = bannerMovie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color(0xEE000000))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "今日精选推荐 · 1080P",
                                fontSize = 11.sp,
                                color = AccentGold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = bannerMovie.title,
                                fontSize = 16.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${bannerMovie.category} · ${bannerMovie.duration}",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                if (categories.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = cat == selectedCategory
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                label = {
                                    Text(
                                        text = cat,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NetflixRed,
                                    selectedLabelColor = Color.White,
                                    containerColor = DarkSurface,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedMovies) { movie ->
                        MovieCard(
                            movie = movie,
                            onClick = { onMovieClick(movie.id) }
                        )
                    }
                }
            }
        }
    }
}

// 离线管理屏幕组件（在同一个包内，彻底避免文件缺失导致的未解析引用）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    onBackClick: () -> Unit,
    onPlayLocalVideo: (String, String) -> Unit
) {
    val context = LocalContext.current
    var videoList by remember { mutableStateOf<List<DownloadedVideoItem>>(emptyList()) }
    var itemToDelete by remember { mutableStateOf<DownloadedVideoItem?>(null) }

    fun refreshList() {
        videoList = DownloadHelper.getDownloadedVideos()
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "下载与离线管理",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (videoList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "暂无离线缓存的视频",
                            color = TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "在影片详情页或播放器中点击「下载」即可在此查看",
                            color = Color(0xFF777777),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(videoList) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface)
                                .padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.sizeText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentGold
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = item.dateText,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onPlayLocalVideo(item.filePath, item.displayName) },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = NetflixRed),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = { itemToDelete = item },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x33FF0000)),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        itemToDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text(text = "删除视频", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "确定要删除本地缓存「${item.displayName}」吗？删除后将彻底释放手机空间。",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val success = DownloadHelper.deleteDownloadedVideo(item.file)
                            if (success) {
                                Toast.makeText(context, "已删除，已释放空间", Toast.LENGTH_SHORT).show()
                                refreshList()
                            } else {
                                Toast.makeText(context, "删除失败，请稍后重试", Toast.LENGTH_SHORT).show()
                            }
                            itemToDelete = null
                        }
                    ) {
                        Text(text = "确认删除", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text(text = "取消", color = Color.White)
                    }
                },
                containerColor = DarkSurface
            )
        }
    }
}
