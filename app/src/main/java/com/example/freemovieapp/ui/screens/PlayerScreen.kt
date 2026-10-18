package com.example.freemovieapp.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import java.util.Locale
import kotlin.math.abs
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

    // 上下控制栏同步显示与 5 秒自动消退状态
    var areControlsVisible by remember { mutableStateOf(true) }
    var userTouchTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var playerViewInstance by remember { mutableStateOf<PlayerView?>(null) }

    // 播放进度与状态
    var currentPositionMs by remember { mutableStateOf(0L) }
    var totalDurationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPositionMs by remember { mutableStateOf(0L) }

    // 手势状态：长按边缘 2.0X 倍速 & 左右滑动调节进度
    var isLongPressFastForwarding by remember { mutableStateOf(false) }
    var isSeekingGesture by remember { mutableStateOf(false) }
    var gestureSeekPositionMs by remember { mutableStateOf(0L) }
    var gestureSeekDeltaSeconds by remember { mutableStateOf(0) }

    // 画面比例模式
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
            .setBackBuffer(30000, true)
            .build()

        ExoPlayer.Builder(context)
            .setBandwidthMeter(bandwidthMeter)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            }
    }

    // 格式化毫秒为 00:00 或 0:00:00
    fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun notifyUserInteraction() {
        areControlsVisible = true
        userTouchTimestamp = System.currentTimeMillis()
    }

    // 定时同步播放进度条
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isDraggingSlider) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            val dur = exoPlayer.duration
            if (dur > 0) {
                totalDurationMs = dur
            }
            isPlaying = exoPlayer.isPlaying
            delay(400L)
        }
    }

    // 5 秒无操作自动隐藏上下控制栏
    LaunchedEffect(areControlsVisible, userTouchTimestamp, showQualityDialog, showSourceDialog, isDraggingSlider) {
        if (areControlsVisible && !showQualityDialog && !showSourceDialog && !isDraggingSlider) {
            delay(5000L)
            areControlsVisible = false
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

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
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
        if (!isLongPressFastForwarding) {
            exoPlayer.playbackParameters = PlaybackParameters(speedOptions[currentSpeedIndex])
        }
    }

    // 全屏横屏与屏幕常亮锁
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
            notifyUserInteraction()
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
            notifyUserInteraction()
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
        // 底层视频画面渲染（关闭原生控制器冲突，改用自定义统一上下 Compose 控制栏）
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // 禁用原生控制器，防止与全屏手势层抢夺焦点
                    keepScreenOn = true
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

        // 全屏智能手势层：
        // 1. 轻触点击 -> 唤醒/隐藏上下进度与控制栏
        // 2. 左右滑动 -> 丝滑调节视频播放进度
        // 3. 长按两侧边缘 -> 2.0X 极速快进，松手即恢复
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentSpeedIndex) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        val totalWidth = size.width.toFloat().coerceAtLeast(1f)
                        val isEdge = (startX < totalWidth * 0.32f || startX > totalWidth * 0.68f)
                        val touchSlop = viewConfiguration.touchSlop

                        var isLongPress2x = false
                        var isHorizontalSeeking = false
                        val seekStartPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val duration = exoPlayer.duration.coerceAtLeast(0L)
                        var targetSeekPos = seekStartPos

                        val downTime = System.currentTimeMillis()
                        var currentPointer = down

                        while (currentPointer.pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            currentPointer = change
                            if (!change.pressed) break

                            val dx = change.position.x - startX
                            val dy = change.position.y - startY

                            if (!isLongPress2x && !isHorizontalSeeking) {
                                if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.2f && duration > 0L) {
                                    // 触发左右滑动调节进度
                                    isHorizontalSeeking = true
                                    isSeekingGesture = true
                                } else if (System.currentTimeMillis() - downTime >= 350L && isEdge && abs(dx) <= touchSlop && abs(dy) <= touchSlop) {
                                    // 触发长按边缘 2.0X 倍速
                                    isLongPress2x = true
                                    isLongPressFastForwarding = true
                                    exoPlayer.playbackParameters = PlaybackParameters(2.0f)
                                }
                            }

                            if (isHorizontalSeeking && duration > 0L) {
                                val deltaMs = ((dx / totalWidth) * 90_000L).toLong()
                                targetSeekPos = (seekStartPos + deltaMs).coerceIn(0L, duration)
                                gestureSeekPositionMs = targetSeekPos
                                gestureSeekDeltaSeconds = (deltaMs / 1000L).toInt()
                                change.consume()
                            }
                        }

                        if (isLongPress2x) {
                            isLongPressFastForwarding = false
                            exoPlayer.playbackParameters = PlaybackParameters(speedOptions[currentSpeedIndex])
                        } else if (isHorizontalSeeking) {
                            isSeekingGesture = false
                            exoPlayer.seekTo(targetSeekPos)
                            currentPositionMs = targetSeekPos
                            userTouchTimestamp = System.currentTimeMillis()
                        } else {
                            areControlsVisible = !areControlsVisible
                            if (areControlsVisible) {
                                userTouchTimestamp = System.currentTimeMillis()
                            }
                        }
                    }
                }
        )

        // 画质增强视觉滤镜层
        if (isVisionBoostEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x06FFFFFF))
            )
        }

        // 左右滑动调节进度的中心实时预览气泡
        AnimatedVisibility(
            visible = isSeekingGesture,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xDD000000))
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                val sign = if (gestureSeekDeltaSeconds >= 0) "+" else ""
                Text(
                    text = "${formatTime(gestureSeekPositionMs)} / ${formatTime(totalDurationMs)}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "进度调节 (${sign}${gestureSeekDeltaSeconds}秒)",
                    color = AccentGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 长按 2.0X 倍速快进中的视觉 HUD 提示徽章
        AnimatedVisibility(
            visible = isLongPressFastForwarding,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xDD000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "2.0X 极速快进中 · 松手恢复",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 切换画质缓冲蒙层
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

        // 顶部控制栏（与底部进度控制栏同步淡入淡出）
        AnimatedVisibility(
            visible = areControlsVisible && !isLongPressFastForwarding,
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
                        onClick = {
                            notifyUserInteraction()
                            showQualityDialog = true
                        },
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
                        onClick = {
                            notifyUserInteraction()
                            isVisionBoostEnabled = !isVisionBoostEnabled
                        },
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
                            notifyUserInteraction()
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
                            onClick = {
                                notifyUserInteraction()
                                showSourceDialog = true
                            },
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
                            notifyUserInteraction()
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
                            notifyUserInteraction()
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
                            onClick = {
                                notifyUserInteraction()
                                currentEpIndex += 1
                            },
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

        // 底部播放进度控制栏（支持拖动进度条、播放/暂停、快退10秒、快进10秒）
        AnimatedVisibility(
            visible = areControlsVisible && !isLongPressFastForwarding,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { it },
            exit = fadeOut(animationSpec = tween(500)) + slideOutVertically(animationSpec = tween(500)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x88000000), Color(0xEE000000))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // 播放 / 暂停按钮
                IconButton(
                    onClick = {
                        notifyUserInteraction()
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // 快退 10 秒按钮
                IconButton(
                    onClick = {
                        notifyUserInteraction()
                        val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                        exoPlayer.seekTo(target)
                        currentPositionMs = target
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "快退10秒",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 快进 10 秒按钮
                IconButton(
                    onClick = {
                        notifyUserInteraction()
                        val dur = totalDurationMs.coerceAtLeast(0L)
                        val target = if (dur > 0) (exoPlayer.currentPosition + 10_000L).coerceAtMost(dur) else exoPlayer.currentPosition + 10_000L
                        exoPlayer.seekTo(target)
                        currentPositionMs = target
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "快进10秒",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 当前播放时间
                val displayPos = if (isDraggingSlider) sliderPositionMs else currentPositionMs
                Text(
                    text = formatTime(displayPos),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                // 可自由拖动的进度条 Slider
                Slider(
                    value = displayPos.toFloat(),
                    onValueChange = { newVal ->
                        isDraggingSlider = true
                        sliderPositionMs = newVal.toLong()
                        notifyUserInteraction()
                    },
                    onValueChangeFinished = {
                        exoPlayer.seekTo(sliderPositionMs)
                        currentPositionMs = sliderPositionMs
                        isDraggingSlider = false
                        notifyUserInteraction()
                    },
                    valueRange = 0f..(totalDurationMs.coerceAtLeast(1L).toFloat()),
                    colors = SliderDefaults.colors(
                        thumbColor = NetflixRed,
                        activeTrackColor = NetflixRed,
                        inactiveTrackColor = Color(0x55FFFFFF)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )

                // 视频总时长
                Text(
                    text = formatTime(totalDurationMs),
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
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
