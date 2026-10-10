package com.example.freemovieapp.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.HighQuality
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
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.freemovieapp.data.MovieRepository
import com.example.freemovieapp.ui.theme.AccentGold
import com.example.freemovieapp.ui.theme.DarkSurface
import com.example.freemovieapp.ui.theme.NetflixRed

data class QualityOption(
    val label: String,
    val description: String,
    val maxWidth: Int,
    val maxHeight: Int,
    val minWidth: Int,
    val minHeight: Int,
    val forceMaxBitrate: Boolean
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    movieId: String,
    initialSourceIndex: Int = 0,
    initialEpisodeIndex: Int = 0,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val movie = MovieRepository.getMovieById(movieId) ?: return
    val activity = context as? Activity

    val activeSource = movie.sources.getOrNull(initialSourceIndex) ?: movie.sources.firstOrNull()
    val episodes = activeSource?.episodes ?: movie.episodes

    var currentEpIndex by remember { mutableStateOf(initialEpisodeIndex) }
    val currentEpisode = episodes.getOrNull(currentEpIndex) ?: return

    // 画质规格选项
    val qualityList = remember {
        listOf(
            QualityOption("4K 极清", "3840×2160 UHD", 3840, 2160, 2560, 1440, true),
            QualityOption("2K 超清", "2560×1440 QHD", 2560, 1440, 1920, 1080, true),
            QualityOption("1080P 全高清", "1920×1080 FHD", 1920, 1080, 1280, 720, true),
            QualityOption("720P 高清", "1280×720 HD", 1280, 720, 854, 480, false),
            QualityOption("自动自适应", "智能根据当前网速匹配", 3840, 2160, 0, 0, false)
        )
    }
    var selectedQualityIndex by remember { mutableStateOf(0) }
    var showQualityDialog by remember { mutableStateOf(false) }

    // 画面比例: 0: 原始适应(FIT), 1: 裁剪铺满(ZOOM), 2: 拉伸全屏(FILL)
    var resizeModeIndex by remember { mutableStateOf(0) }
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    val resizeModeNames = listOf("默认比例", "裁剪铺满", "拉伸全屏")

    // 倍速播放
    val speedOptions = listOf(1.0f, 1.25f, 1.5f, 2.0f)
    var currentSpeedIndex by remember { mutableStateOf(0) }

    // ExoPlayer 核心初始化（配置 4K/2K 硬解码管线与自适应带宽）
    val exoPlayer = remember {
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(30_000_000L) // 初始 30Mbps，强迫拉取超清 4K/1080P 首帧
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(3840, 2160) // 开放 4K 超清解码通道
                    .setForceHighestSupportedBitrate(true)
                    .setTunnelingEnabled(true)
            )
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30000, 150000, 2000, 4000)
            .build()

        ExoPlayer.Builder(context)
            .setBandwidthMeter(bandwidthMeter)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
    }

    // 响应画质切换
    fun applyQuality(quality: QualityOption) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(quality.maxWidth, quality.maxHeight)
            .setMinVideoSize(quality.minWidth, quality.minHeight)
            .setForceHighestSupportedBitrate(quality.forceMaxBitrate)
            .build()
    }

    LaunchedEffect(currentEpIndex) {
        val mediaItem = MediaItem.fromUri(currentEpisode.url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        applyQuality(qualityList[selectedQualityIndex])
    }

    LaunchedEffect(currentSpeedIndex) {
        exoPlayer.playbackParameters = PlaybackParameters(speedOptions[currentSpeedIndex])
    }

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

        // 顶部控制与画质指示栏
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
                        text = "正在播放: ${currentEpisode.name} · ${qualityList[selectedQualityIndex].label}",
                        color = AccentGold,
                        fontSize = 12.sp
                    )
                }
            }

            // 右上角交互按钮组：画质切换 + 倍速 + 画面比例 + 下一集
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 画质选择按钮
                Button(
                    onClick = { showQualityDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x88E50914)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = qualityList[selectedQualityIndex].label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 倍速按钮
                Button(
                    onClick = {
                        currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x66000000)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${speedOptions[currentSpeedIndex]}x",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 画面比例按钮
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
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = resizeModeNames[resizeModeIndex],
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }

                // 下一集按钮
                if (currentEpIndex < episodes.size - 1) {
                    Spacer(modifier = Modifier.width(6.dp))
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
                        Spacer(modifier = Modifier.width(2.dp))
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

        // 画质选择弹窗
        if (showQualityDialog) {
            Dialog(onDismissRequest = { showQualityDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurface,
                    modifier = Modifier
                        .width(320.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "选择播放清晰度",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(qualityList.indices.toList()) { idx ->
                                val opt = qualityList[idx]
                                val isSelected = (idx == selectedQualityIndex)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) NetflixRed else Color(0x33FFFFFF))
                                        .clickable {
                                            selectedQualityIndex = idx
                                            applyQuality(opt)
                                            showQualityDialog = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = opt.label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = opt.description,
                                            fontSize = 11.sp,
                                            color = if (isSelected) Color(0xEEFFFFFF) else Color(0xFFAAAAAA)
                                        )
                                    }
                                    if (isSelected) {
                                        Text(
                                            text = "当前使用",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentGold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
