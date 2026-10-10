package com.example.freemovieapp.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.freemovieapp.data.MovieRepository
import com.example.freemovieapp.ui.theme.AccentGold

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    movieId: String,
    initialEpisodeIndex: Int = 0,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val movie = MovieRepository.getMovieById(movieId) ?: return
    val activity = context as? Activity

    var currentEpIndex by remember { mutableStateOf(initialEpisodeIndex) }
    val currentEpisode = movie.episodes.getOrNull(currentEpIndex) ?: return

    // 画面比例模式: 0: 原始适应(FIT), 1: 裁剪铺满(ZOOM), 2: 拉伸(FILL)
    var resizeModeIndex by remember { mutableStateOf(0) }
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    val resizeModeNames = listOf("默认适应", "居中铺满", "全屏拉伸")

    // 高清画质与自适应码率配置 (锁定最高分辨率与充足缓存)
    val exoPlayer = remember {
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true) // 强制优先解码最高画质轨道
                    .setTunnelingEnabled(true)
            )
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                25000, // 最小缓冲 25 秒
                120000, // 最大缓冲 120 秒
                2000,  // 播放启动缓冲 2 秒
                4000   // 重新缓冲 4 秒
            )
            .build()

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
    }

    // 切换剧集时重新载入媒体流
    LaunchedEffect(currentEpIndex) {
        val mediaItem = MediaItem.fromUri(currentEpisode.url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // 处理横屏与全屏隐藏状态栏
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            exoPlayer.release()
        }
    }

    BackHandler {
        onBackClick()
    }

    var playerViewInstance by remember { mutableStateOf<PlayerView?>(null) }

    // 响应画面比例切换
    LaunchedEffect(resizeModeIndex) {
        playerViewInstance?.resizeMode = resizeModes[resizeModeIndex]
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = resizeModes[resizeModeIndex]
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 顶部控制与剧集状态指示
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopStart)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackClick,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x66000000))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = movie.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "正在播放: ${currentEpisode.name} · 1080P 超清增强",
                        color = AccentGold,
                        fontSize = 12.sp
                    )
                }
            }

            // 右上角：切换比例 & 下一集
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 画面比例切换按钮
                Button(
                    onClick = {
                        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x66000000)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = resizeModeNames[resizeModeIndex],
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }

                // 下一集按钮
                if (currentEpIndex < movie.episodes.size - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { currentEpIndex += 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x99E50914)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "下一集",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
