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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class RealQualityItem(
    val name: String,
    val resolutionText: String,
    val trackGroupIndex: Int = -1,
    val trackIndex: Int = -1,
    val targetHeight: Int = 1080
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
    val coroutineScope = rememberCoroutineScope()

    var currentSourceIndex by remember { mutableStateOf(initialSourceIndex) }
    var currentEpIndex by remember { mutableStateOf(initialEpisodeIndex) }

    val currentSource = movie.sources.getOrNull(currentSourceIndex) ?: movie.sources.firstOrNull()
    val episodes = currentSource?.episodes ?: movie.episodes
    val currentEpisode = episodes.getOrNull(currentEpIndex) ?: return

    // 实时检测画面真实物理分辨率
    var realVideoResolution by remember { mutableStateOf("检测中...") }
    var isSwitchingQuality by remember { mutableStateOf(false) }
    var switchingQualityMessage by remember { mutableStateOf("") }

    // 动态提取视频流中真实存在的多码率轨道
    var availableTracks by remember { mutableStateOf<List<RealQualityItem>>(emptyList()) }
    var selectedQualityLabel by remember { mutableStateOf("1080P 超清") }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }

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

    // ExoPlayer 核心与带宽侦测
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(3840, 2160) // 开放 4K 硬件解码
                    .setForceHighestSupportedBitrate(true)
            )
        }
    }

    val exoPlayer = remember {
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(35_000_000L)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30000, 150000, 2000, 4000)
            .build()

        ExoPlayer.Builder(context)
            .setBandwidthMeter(bandwidthMeter)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
    }

    // 监听播放器实时视频尺寸与真实轨道
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val tag = when {
                        videoSize.height >= 2160 -> "4K 极清"
                        videoSize.height >= 1440 -> "2K 超清"
                        videoSize.height >= 1080 -> "1080P"
                        videoSize.height >= 720 -> "720P"
                        else -> "标清"
                    }
                    realVideoResolution = "$tag (${videoSize.width}×${videoSize.height})"
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val list = mutableListOf<RealQualityItem>()
                for ((groupIndex, group) in tracks.groups.withIndex()) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val w = format.width
                            val h = format.height
                            val bitrate = format.bitrate
                            val bitrateText = if (bitrate > 0) "${bitrate / 1000 / 1000}M" else "原画"
                            val label = when {
                                h >= 2160 -> "4K 极清 ($bitrateText)"
                                h >= 1440 -> "2K 超清 ($bitrateText)"
                                h >= 1080 -> "1080P 全高清 ($bitrateText)"
                                h >= 720 -> "720P 高清 ($bitrateText)"
                                else -> "480P 标清 ($bitrateText)"
                            }
                            list.add(
                                RealQualityItem(
                                    name = label,
                                    resolutionText = "${w}×${h}",
                                    trackGroupIndex = groupIndex,
                                    trackIndex = i,
                                    targetHeight = h
                                )
                            )
                        }
                    }
                }
                if (list.isNotEmpty()) {
                    availableTracks = list.sortedByDescending { it.targetHeight }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // 载入或切换媒体流
    fun loadMediaStream(seekPositionMs: Long = 0L) {
        val mediaItem = MediaItem.fromUri(currentEpisode.url)
        exoPlayer.setMediaItem(mediaItem)
        if (seekPositionMs > 0) {
            exoPlayer.seekTo(seekPositionMs)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(currentSourceIndex, currentEpIndex) {
        loadMediaStream()
    }

    LaunchedEffect(currentSpeedIndex) {
        exoPlayer.playbackParameters = PlaybackParameters(speedOptions[currentSpeedIndex])
    }

    // 全屏与横屏处理
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

    // 执行真实的画质切换：重置缓冲区并重新同步分片
    fun executeQualitySwitch(item: RealQualityItem) {
        coroutineScope.launch {
            isSwitchingQuality = true
            switchingQualityMessage = "正在切换至 ${item.name}，正在拉取高码率分片..."
            val currentPos = exoPlayer.currentPosition

            selectedQualityLabel = item.name

            if (item.trackGroupIndex >= 0 && item.trackIndex >= 0) {
                // 如果当前流原生包含多轨道，直接强锁轨道
                val currentTracks = exoPlayer.currentTracks
                val group = currentTracks.groups.getOrNull(item.trackGroupIndex)
                if (group != null) {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(item.trackIndex)))
                        .build()
                }
            } else {
                // 通过高度与码率参数强行下发约束
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .setMaxVideoSize(item.targetHeight * 16 / 9, item.targetHeight)
                        .setMinVideoSize(item.targetHeight * 16 / 9, item.targetHeight)
                        .setForceHighestSupportedBitrate(true)
                )
            }

            // 触发真实的冲刷缓冲与重新缓冲
            exoPlayer.seekTo(currentPos)
            delay(1200)
            isSwitchingQuality = false
        }
    }

    // 执行线路切换（换线提升画质）
    fun executeSourceSwitch(sourceIndex: Int) {
        coroutineScope.launch {
            isSwitchingQuality = true
            val src = movie.sources[sourceIndex]
            switchingQualityMessage = "正在切换至 [${src.name}]，正在载入全新超清流..."
            val currentPos = exoPlayer.currentPosition
            currentSourceIndex = sourceIndex

            delay(800)
            loadMediaStream(currentPos)
            delay(1000)
            isSwitchingQuality = false
        }
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

        // 切换画质时的真实缓冲蒙层
        if (isSwitchingQuality) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NetflixRed, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = switchingQualityMessage,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "正在重新锁定原画数据切片，请稍候...",
                        color = AccentGold,
                        fontSize = 11.sp
                    )
                }
            }
        }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${currentEpisode.name} · ",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "物理画面: $realVideoResolution",
                            color = AccentGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 右上角按钮组：画质切换 + 换线 + 倍速 + 比例 + 下一集
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 1. 画质选择按钮
                Button(
                    onClick = { showQualityDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x99E50914)),
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
                        text = selectedQualityLabel.substringBefore(" "),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. 换线按钮（如果有多条线路）
                if (movie.sources.size > 1) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { showSourceDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x66000000)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "换线",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 3. 倍速按钮
                Button(
                    onClick = {
                        currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x66000000)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
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

                // 4. 画面比例
                Button(
                    onClick = {
                        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x66000000)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
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

                // 5. 下一集
                if (currentEpIndex < episodes.size - 1) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { currentEpIndex += 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x99E50914)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
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
                        .width(340.dp)
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "画质切换与分辨率锁定",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "当前真实渲染物理分辨率: $realVideoResolution",
                            fontSize = 11.sp,
                            color = AccentGold,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                        )

                        // 备选画质列表
                        val displayOptions = remember(availableTracks) {
                            if (availableTracks.isNotEmpty()) {
                                availableTracks
                            } else {
                                listOf(
                                    RealQualityItem("4K 极清 (2160P)", "锁定 4K UHD 极高码率", targetHeight = 2160),
                                    RealQualityItem("2K 超清 (1440P)", "锁定 2K QHD 超高清", targetHeight = 1440),
                                    RealQualityItem("1080P 全高清", "锁定 1080P FHD 蓝光画质", targetHeight = 1080),
                                    RealQualityItem("720P 高清", "1280×720 HD 省流模式", targetHeight = 720)
                                )
                            }
                        }

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(displayOptions) { item ->
                                val isSelected = item.name.startsWith(selectedQualityLabel.substringBefore(" "))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) NetflixRed else Color(0x22FFFFFF))
                                        .clickable {
                                            showQualityDialog = false
                                            executeQualitySwitch(item)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = item.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = item.resolutionText,
                                            fontSize = 11.sp,
                                            color = if (isSelected) Color(0xEEFFFFFF) else Color(0xFFAAAAAA)
                                        )
                                    }
                                    if (isSelected) {
                                        Text(
                                            text = "使用中",
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

        // 线路选择弹窗
        if (showSourceDialog) {
            Dialog(onDismissRequest = { showSourceDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurface,
                    modifier = Modifier
                        .width(320.dp)
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "选择更高画质线路",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "不同线路转码码率不同，切换线路可获得更高画质",
                            fontSize = 11.sp,
                            color = Color(0xFFAAAAAA),
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                        )

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(movie.sources) { idx, src ->
                                val isSelected = (idx == currentSourceIndex)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AccentGold else Color(0x22FFFFFF))
                                        .clickable {
                                            showSourceDialog = false
                                            executeSourceSwitch(idx)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = src.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "当前线",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
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
