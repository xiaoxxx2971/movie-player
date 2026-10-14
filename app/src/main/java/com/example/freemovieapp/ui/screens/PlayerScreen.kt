package com.example.freemovieapp.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.freemovieapp.data.DownloadHelper
import com.example.freemovieapp.data.MovieRepository
import com.example.freemovieapp.ui.theme.AccentGold
import com.example.freemovieapp.ui.theme.DarkSurface
import com.example.freemovieapp.ui.theme.NetflixRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

data class QualityOptionItem(
    val id: String,
    val label: String,
    val subText: String,
    val targetWidth: Int,
    val targetHeight: Int
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

    var realVideoResolution by remember { mutableStateOf("1080P 蓝光原画") }
    var isSwitchingQuality by remember { mutableStateOf(false) }
    var switchingQualityMessage by remember { mutableStateOf("") }

    var selectedQualityId by remember { mutableStateOf("1080p") }
    var selectedQualityDisplayLabel by remember { mutableStateOf("1080P 全高清") }

    var isVisionBoostEnabled by remember { mutableStateOf(true) }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }

    var areControlsVisible by remember { mutableStateOf(true) }
    var playerViewInstance by remember { mutableStateOf<PlayerView?>(null) }

    var resizeModeIndex by remember { mutableStateOf(0) }
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    val resizeModeNames = listOf("默认比例", "裁剪铺满", "拉伸全屏")

    val speedOptions = listOf(1.0f, 1.25f, 1.5f, 2.0f)
    var currentSpeedIndex by remember { mutableStateOf(0) }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(3840, 2160)
                    .setForceHighestSupportedBitrate(true)
                    .setPreferredVideoMimeType("video/hevc")
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
            )
        }
    }

    val exoPlayer = remember {
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(50_000_000L)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(35000, 180000, 2500, 5000)
            .build()

        ExoPlayer.Builder(context)
            .setBandwidthMeter(bandwidthMeter)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            }
    }

    fun parseVideoQualityTag(width: Int, height: Int): String {
        val maxDim = max(width, height)
        return when {
            maxDim >= 3840 || height >= 2160 -> "4K 极清"
            maxDim >= 2560 || height >= 1440 -> "2K 超清"
            maxDim >= 1920 || height >= 800 -> "1080P 全高清"
            maxDim >= 1280 || height >= 500 -> "720P 高清"
            else -> "480P 标清"
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val tag = parseVideoQualityTag(videoSize.width, videoSize.height)
                    realVideoResolution = "$tag (${videoSize.width}×${videoSize.height})"
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

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

    LaunchedEffect(resizeModeIndex) {
        playerViewInstance?.resizeMode = resizeModes[resizeModeIndex]
    }

    val completeQualityLadder = remember {
        listOf(
            QualityOptionItem("4k", "4K 极清", "3840×2160 UHD · 超清画质增强 / 视网膜渲染", 3840, 2160),
            QualityOptionItem("2k", "2K 超清", "2560×1440 QHD · 原画细节深度锐化", 2560, 1440),
            QualityOptionItem("1080p", "1080P 全高清", "1920×1080 FHD · 蓝光原画 (当前片源最佳)", 1920, 1080),
            QualityOptionItem("720p", "720P 高清", "1280×720 HD · 流畅极速省流模式", 1280, 720),
            QualityOptionItem("auto", "自动自适应", "智能根据手机网络实时码率适配", 3840, 2160)
        )
    }

    fun executeQualitySwitch(item: QualityOptionItem) {
        coroutineScope.launch {
            isSwitchingQuality = true
            selectedQualityId = item.id
            selectedQualityDisplayLabel = item.label
            switchingQualityMessage = "正在锁定 [${item.label}] 并重新拉取原画数据流..."

            val currentPos = exoPlayer.currentPosition

            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setMaxVideoSize(item.targetWidth, item.targetHeight)
                    .setMinVideoSize(item.targetWidth / 2, item.targetHeight / 2)
                    .setForceHighestSupportedBitrate(item.id != "auto" && item.id != "720p")
            )

            if ((item.id == "4k" || item.id == "2k") && movie.sources.size > 1) {
                val hdSourceIndex = movie.sources.indexOfFirst {
                    it.name.contains("索尼", ignoreCase = true) || it.name.contains("1080", ignoreCase = true) || it.name.contains("蓝光")
                }
                if (hdSourceIndex >= 0 && hdSourceIndex != currentSourceIndex) {
                    currentSourceIndex = hdSourceIndex
                    loadMediaStream(currentPos)
                } else {
                    exoPlayer.seekTo(currentPos)
                }
            } else {
                exoPlayer.seekTo(currentPos)
            }

            delay(1200)
            isSwitchingQuality = false
        }
    }

    fun executeSourceSwitch(sourceIndex: Int) {
        coroutineScope.launch {
            isSwitchingQuality = true
            val src = movie.sources[sourceIndex]
            switchingQualityMessage = "正在切换至 [${src.name}] 专线，载入中..."
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
                    controllerShowTimeoutMs = 5000
                    resizeMode = resizeModes[resizeModeIndex]
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        areControlsVisible = (visibility == View.VISIBLE)
                    })

                    playerViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!areControlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        playerViewInstance?.showController()
                        areControlsVisible = true
                    }
            )
        }

        if (isVisionBoostEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x06FFFFFF))
            )
        }

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

        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { -it },
            exit = fadeOut(animationSpec = tween(500)) + slideOutVertically(animationSpec = tween(500)) { -it },
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xEE000000), Color(0x66000000), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { showQualityDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
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
                            text = selectedQualityDisplayLabel.substringBefore(" "),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { isVisionBoostEnabled = !isVisionBoostEnabled },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVisionBoostEnabled) Color(0x99FFB800) else Color(0x66000000)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (isVisionBoostEnabled) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (isVisionBoostEnabled) "增强开" else "增强关",
                            color = if (isVisionBoostEnabled) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            DownloadHelper.downloadVideo(
                                context = context,
                                movieTitle = movie.title,
                                episodeName = currentEpisode.name,
                                videoUrl = currentEpisode.url
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x66000000)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "下载",
                            tint = AccentGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "下载",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }

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
        }

        if (showQualityDialog) {
            Dialog(onDismissRequest = { showQualityDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurface,
                    modifier = Modifier
                        .width(360.dp)
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

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(completeQualityLadder) { item ->
                                val isSelected = (item.id == selectedQualityId)
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = item.subText,
                                            fontSize = 11.sp,
                                            color = if (isSelected) Color(0xEEFFFFFF) else Color(0xFFAAAAAA)
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "已选中",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

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
                            text = "切换播放线路",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "不同线路转码码率不同，切换专线可获得原画效果",
                            fontSize = 11.sp,
                            color = Color(0xFFAAAAAA),
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                        )

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(movie.sources.indices.toList()) { idx ->
                                val src = movie.sources[idx]
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
