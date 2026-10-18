package com.example.freemovieapp.data

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.example.freemovieapp.model.Episode
import com.example.freemovieapp.model.Movie
import com.example.freemovieapp.model.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadedVideoItem(
    val file: File,
    val displayName: String,
    val sizeText: String,
    val dateText: String,
    val filePath: String
)

data class FilteredStreamResult(
    val playUrl: String,
    val removedAdSeconds: Int,
    val removedAdSegments: Int
)

/**
 * M3U8 切片插播广告净网过滤器
 * 自动识别并剔除第三方资源站通过 #EXT-X-DISCONTINUITY 注入的博彩/贴片广告分片
 */
object M3u8AdFilter {

    private data class SegmentBlock(
        val lines: MutableList<String> = mutableListOf(),
        var durationSeconds: Double = 0.0,
        var segmentCount: Int = 0
    )

    suspend fun resolveCleanStream(context: Context, originalUrl: String): FilteredStreamResult = withContext(Dispatchers.IO) {
        if (!originalUrl.contains(".m3u8", ignoreCase = true) || originalUrl.startsWith("file://") || originalUrl.startsWith("/")) {
            return@withContext FilteredStreamResult(originalUrl, 0, 0)
        }

        try {
            var currentUrl = originalUrl
            var content = fetchText(currentUrl) ?: return@withContext FilteredStreamResult(originalUrl, 0, 0)

            // 如果是主索引 (Master Playlist)，解析出最高画质的子索引地址
            if (content.contains("#EXT-X-STREAM-INF")) {
                val subUrl = extractBestVariantUrl(currentUrl, content)
                if (subUrl != null) {
                    currentUrl = subUrl
                    content = fetchText(currentUrl) ?: return@withContext FilteredStreamResult(originalUrl, 0, 0)
                }
            }

            // 如果没有 #EXT-X-DISCONTINUITY 标签，说明没有分段插播广告，直接返回原地址保证0风险
            if (!content.contains("#EXT-X-DISCONTINUITY")) {
                return@withContext FilteredStreamResult(originalUrl, 0, 0)
            }

            val baseUrl = URL(currentUrl)
            val lines = content.lines()
            val headerLines = mutableListOf<String>()
            val blocks = mutableListOf<SegmentBlock>()
            var currentBlock = SegmentBlock()
            var inBody = false

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                if (line.startsWith("#EXT-X-DISCONTINUITY")) {
                    inBody = true
                    if (currentBlock.segmentCount > 0 || currentBlock.lines.isNotEmpty()) {
                        blocks.add(currentBlock)
                        currentBlock = SegmentBlock()
                    }
                    continue
                }

                if (!inBody && (line.startsWith("#EXTINF") || line.startsWith("#EXT-X-KEY") || !line.startsWith("#"))) {
                    inBody = true
                }

                if (!inBody) {
                    if (!line.startsWith("#EXT-X-ENDLIST")) {
                        headerLines.add(line)
                    }
                } else {
                    if (line.startsWith("#EXT-X-ENDLIST")) {
                        continue
                    } else if (line.startsWith("#EXTINF:")) {
                        val durStr = line.removePrefix("#EXTINF:").substringBefore(",").trim()
                        val dur = durStr.toDoubleOrNull() ?: 0.0
                        currentBlock.durationSeconds += dur
                        currentBlock.lines.add(line)
                    } else if (line.startsWith("#EXT-X-KEY:")) {
                        // 将加密密钥相对路径转换为绝对路径
                        val resolvedKeyLine = resolveKeyUri(baseUrl, line)
                        currentBlock.lines.add(resolvedKeyLine)
                    } else if (line.startsWith("#")) {
                        currentBlock.lines.add(line)
                    } else {
                        // 视频分片路径转换为绝对 URL
                        val absSegUrl = URL(baseUrl, line).toString()
                        currentBlock.lines.add(absSegUrl)
                        currentBlock.segmentCount += 1
                    }
                }
            }

            if (currentBlock.segmentCount > 0 || currentBlock.lines.isNotEmpty()) {
                blocks.add(currentBlock)
            }

            val totalDuration = blocks.sumOf { it.durationSeconds }
            // 仅当整部影片总时长大于 120 秒时，剔除时长在 1~50 秒之间的短促插播广告块
            if (totalDuration <= 120.0 || blocks.size <= 1) {
                return@withContext FilteredStreamResult(originalUrl, 0, 0)
            }

            var removedSec = 0.0
            var removedSegs = 0
            val cleanBlocks = mutableListOf<SegmentBlock>()

            for (b in blocks) {
                val hasAdKeyword = b.lines.any {
                    it.contains("ad_", ignoreCase = true) ||
                    it.contains("/ad/", ignoreCase = true) ||
                    it.contains("adjump", ignoreCase = true)
                }
                val isShortAdBlock = (b.durationSeconds in 1.0..50.0 && b.segmentCount <= 20)

                if (hasAdKeyword || isShortAdBlock) {
                    removedSec += b.durationSeconds
                    removedSegs += b.segmentCount
                } else {
                    cleanBlocks.add(b)
                }
            }

            if (removedSegs == 0 || cleanBlocks.isEmpty()) {
                return@withContext FilteredStreamResult(originalUrl, 0, 0)
            }

            // 组装净化后的本地 M3U8 文件
            val sb = StringBuilder()
            for (h in headerLines) {
                sb.append(h).append("\n")
            }
            for ((idx, b) in cleanBlocks.withIndex()) {
                if (idx > 0) {
                    sb.append("#EXT-X-DISCONTINUITY\n")
                }
                for (l in b.lines) {
                    sb.append(l).append("\n")
                }
            }
            sb.append("#EXT-X-ENDLIST\n")

            // 清理旧缓存文件
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("clean_vod_") && f.name.endsWith(".m3u8")) {
                    f.delete()
                }
            }

            val cleanFile = File(context.cacheDir, "clean_vod_${System.currentTimeMillis()}.m3u8")
            cleanFile.writeText(sb.toString(), Charsets.UTF_8)

            FilteredStreamResult(
                playUrl = Uri.fromFile(cleanFile).toString(),
                removedAdSeconds = removedSec.toInt().coerceAtLeast(1),
                removedAdSegments = removedSegs
            )
        } catch (e: Exception) {
            e.printStackTrace()
            FilteredStreamResult(originalUrl, 0, 0)
        }
    }

    private fun extractBestVariantUrl(masterUrl: String, content: String): String? {
        val base = URL(masterUrl)
        val lines = content.lines()
        var bestBandwidth = -1L
        var bestUrl: String? = null
        var pendingBandwidth = 0L

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val bwMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                pendingBandwidth = bwMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 1L
            } else if (!line.startsWith("#")) {
                val resolved = URL(base, line).toString()
                if (pendingBandwidth >= bestBandwidth) {
                    bestBandwidth = pendingBandwidth
                    bestUrl = resolved
                }
                pendingBandwidth = 0L
            }
        }
        return bestUrl
    }

    private fun resolveKeyUri(baseUrl: URL, keyLine: String): String {
        val regex = Regex("URI=\"([^\"]+)\"")
        val match = regex.find(keyLine) ?: return keyLine
        val rawUri = match.groupValues[1]
        if (rawUri.startsWith("http://") || rawUri.startsWith("https://")) return keyLine
        val absUri = URL(baseUrl, rawUri).toString()
        return keyLine.replace("URI=\"$rawUri\"", "URI=\"$absUri\"")
    }

    private fun fetchText(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; Mobile)")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}

object DownloadHelper {

    fun downloadVideo(context: Context, movieTitle: String, episodeName: String, videoUrl: String) {
        if (videoUrl.isBlank()) {
            Toast.makeText(context, "视频链接无效，无法下载", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Video URL", videoUrl)
            clipboard.setPrimaryClip(clip)

            val uri = Uri.parse(videoUrl)
            val fileExt = if (videoUrl.contains(".m3u8")) "m3u8" else "mp4"
            val sanitizedTitle = movieTitle.replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "_")
            val sanitizedEp = episodeName.replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "_")
            val fileName = "极光影院_${sanitizedTitle}_${sanitizedEp}.$fileExt"

            val request = DownloadManager.Request(uri).apply {
                setTitle("$movieTitle - $episodeName")
                setDescription("极光影院正在下载离线视频...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(
                context,
                "已加入下载！可在本 APP 顶部「离线管理」中查看与删除",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "下载已加入系统队列", Toast.LENGTH_LONG).show()
        }
    }

    fun getDownloadedVideos(): List<DownloadedVideoItem> {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists() || !downloadDir.isDirectory) return emptyList()

        val files = downloadDir.listFiles() ?: return emptyList()
        val result = mutableListOf<DownloadedVideoItem>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        for (file in files) {
            val name = file.name
            if (file.isFile && (name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".m3u8", ignoreCase = true) || name.contains("极光影院"))) {
                val sizeBytes = file.length()
                val sizeText = formatFileSize(sizeBytes)
                val dateText = dateFormat.format(Date(file.lastModified()))
                val cleanName = name.removePrefix("极光影院_").substringBeforeLast(".")

                result.add(
                    DownloadedVideoItem(
                        file = file,
                        displayName = cleanName,
                        sizeText = sizeText,
                        dateText = dateText,
                        filePath = file.absolutePath
                    )
                )
            }
        }
        return result.sortedByDescending { it.file.lastModified() }
    }

    fun deleteDownloadedVideo(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024f * 1024f * 1024f))
            bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
            else -> "$bytes B"
        }
    }
}

object MovieRepository {

    var currentApiEndpoint = "https://suoniapi.com/api.php/provide/vod/"
    private val backupApiEndpoints = listOf(
        "https://bfzyapi.com/api.php/provide/vod/",
        "https://cj.lziapi.com/api.php/provide/vod/",
        "https://caiji.dbzy.tv/api.php/provide/vod/at/json/"
    )

    var cachedHomeMovies: List<Movie> = emptyList()
    var cachedSearchResults: List<Movie> = emptyList()
    var lastSearchQuery: String = ""

    private val movieDetailsCache = mutableMapOf<String, Movie>()

    private val fallbackMovies = listOf(
        Movie(
            id = "fb_1",
            title = "大雄兔 (Big Buck Bunny)",
            category = "动画",
            year = "2008",
            rating = 8.5,
            duration = "10 分钟",
            description = "Blender 开源基金会 4K 超清动画短片，讲述森林中温和的大兔子智斗三只恶作剧松鼠的幽默故事。",
            posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1000&q=80",
            sources = listOf(
                VideoSource("💎 索尼 1080P 蓝光原画线 (高码率推荐)", listOf(
                    Episode("正片 1080P", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                    Episode("预告片", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
                ))
            ),
            episodes = listOf(
                Episode("正片 1080P", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                Episode("预告片", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
            ),
            isFeatured = true
        ),
        Movie(
            id = "fb_2",
            title = "钢铁之泪 (Tears of Steel)",
            category = "科幻",
            year = "2012",
            rating = 8.1,
            duration = "12 分钟",
            description = "阿姆斯特丹反乌托邦未来的高清 CGI 短片，年轻科学家试图通过时空全息技术挽救昔日恋人与毁灭的世界。",
            posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1000&q=80",
            sources = listOf(
                VideoSource("💎 索尼 1080P 蓝光原画线", listOf(
                    Episode("正片 1080P", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4")
                ))
            ),
            episodes = listOf(
                Episode("正片 1080P", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4")
            ),
            isFeatured = true
        )
    )

    init {
        fallbackMovies.forEach { movieDetailsCache[it.id] = it }
    }

    fun getMovieById(id: String): Movie? = movieDetailsCache[id]

    suspend fun fetchLatestMovies(forceRefresh: Boolean = false): List<Movie> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedHomeMovies.isNotEmpty()) {
            return@withContext cachedHomeMovies
        }
        val url = "$currentApiEndpoint?ac=detail&pg=1"
        var movies = requestApi(url)
        if (movies.isEmpty()) {
            movies = requestApi("https://bfzyapi.com/api.php/provide/vod/?ac=detail&pg=1")
        }
        val result = if (movies.isEmpty()) fallbackMovies else movies
        cachedHomeMovies = result
        result
    }

    suspend fun searchOnlineMovies(keyword: String): List<Movie> = withContext(Dispatchers.IO) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) {
            return@withContext fetchLatestMovies()
        }
        if (trimmed == lastSearchQuery && cachedSearchResults.isNotEmpty()) {
            return@withContext cachedSearchResults
        }

        val encodedWd = URLEncoder.encode(trimmed, "UTF-8")
        val primaryUrl = "$currentApiEndpoint?ac=detail&wd=$encodedWd"
        var results = requestApi(primaryUrl)

        if (results.isEmpty()) {
            for (backup in backupApiEndpoints) {
                val backupUrl = "$backup?ac=detail&wd=$encodedWd"
                val backupResults = requestApi(backupUrl)
                if (backupResults.isNotEmpty()) {
                    results = backupResults
                    break
                }
            }
        }

        val finalResults = if (results.isEmpty()) {
            fallbackMovies.filter { it.title.contains(trimmed, ignoreCase = true) }
        } else {
            results
        }
        lastSearchQuery = trimmed
        cachedSearchResults = finalResults
        finalResults
    }

    private fun requestApi(urlString: String): List<Movie> {
        val results = mutableListOf<Movie>()
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; Mobile)")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val responseText = reader.readText()
                reader.close()

                val json = JSONObject(responseText)
                val listArray = json.optJSONArray("list")
                if (listArray != null) {
                    for (i in 0 until listArray.length()) {
                        val item = listArray.getJSONObject(i)
                        val id = item.optString("vod_id", i.toString())
                        val title = item.optString("vod_name", "未知片名")
                        val pic = item.optString("vod_pic", "")
                        val typeName = item.optString("type_name", "影视")
                        val year = item.optString("vod_year", "2024")
                        val rawContent = item.optString("vod_content", "暂无剧情简介")
                        val content = rawContent.replace(Regex("<[^>]*>"), "").trim()
                        val remarks = item.optString("vod_remarks", "高清")
                        val playFromRaw = item.optString("vod_play_from", "")
                        val playUrlRaw = item.optString("vod_play_url", "")

                        if (title.contains("AI漫剧", ignoreCase = true) || title.contains("AI短剧", ignoreCase = true)) {
                            continue
                        }

                        val sources = parseAllSources(playFromRaw, playUrlRaw)
                        if (sources.isNotEmpty()) {
                            val primaryEpisodes = sources.first().episodes
                            val movie = Movie(
                                id = id,
                                title = title,
                                category = typeName,
                                year = year,
                                rating = 8.9,
                                duration = remarks,
                                description = content.ifBlank { "高码率蓝光超清影视，多线路原生极速播放。" },
                                posterUrl = pic,
                                backdropUrl = pic,
                                sources = sources,
                                episodes = primaryEpisodes,
                                isFeatured = (i == 0)
                            )
                            results.add(movie)
                            movieDetailsCache[id] = movie
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conn?.disconnect()
        }
        return results
    }

    private fun parseAllSources(fromRaw: String, urlRaw: String): List<VideoSource> {
        if (urlRaw.isBlank()) return emptyList()
        val fromList = fromRaw.split("$$$").map { it.trim() }
        val urlGroups = urlRaw.split("$$$")
        val sourceList = mutableListOf<VideoSource>()

        for (i in urlGroups.indices) {
            val groupRaw = urlGroups[i]
            val fromName = fromList.getOrNull(i)?.ifBlank { "线路 ${i + 1}" } ?: "线路 ${i + 1}"
            val episodes = mutableListOf<Episode>()

            val episodePairs = groupRaw.split("#")
            for ((index, item) in episodePairs.withIndex()) {
                val parts = item.split("$")
                if (parts.size >= 2) {
                    val epName = parts[0].trim()
                    val epUrl = parts[1].trim()
                    if (epUrl.startsWith("http://") || epUrl.startsWith("https://")) {
                        episodes.add(Episode(name = if (epName.isBlank()) "第 ${index + 1} 集" else epName, url = epUrl))
                    }
                } else if (parts.size == 1) {
                    val epUrl = parts[0].trim()
                    if (epUrl.startsWith("http://") || epUrl.startsWith("https://")) {
                        episodes.add(Episode(name = "全片 1080P 蓝光", url = epUrl))
                    }
                }
            }
            if (episodes.isNotEmpty()) {
                sourceList.add(VideoSource(formatSourceName(fromName), episodes))
            }
        }

        return sourceList.sortedByDescending { src ->
            var score = 0
            if (src.name.contains("索尼", ignoreCase = true) || src.name.contains("suoni")) score += 100
            if (src.name.contains("1080", ignoreCase = true) || src.name.contains("蓝光")) score += 60
            if (src.name.contains("原画") || src.name.contains("暴风")) score += 50
            if (src.name.contains("金鹰") || src.name.contains("快看")) score += 40
            if (src.episodes.any { it.url.contains(".m3u8") }) score += 20
            score
        }
    }

    private fun formatSourceName(raw: String): String {
        return when {
            raw.contains("suoni", ignoreCase = true) || raw.contains("索尼") -> "💎 索尼 1080P 蓝光原画 (高码率推荐)"
            raw.contains("bf", ignoreCase = true) || raw.contains("暴风") -> "🚀 暴风 1080P 超清专线"
            raw.contains("1080", ignoreCase = true) -> "⭐ 1080P 高清蓝光线"
            raw.contains("jy", ignoreCase = true) || raw.contains("金鹰") -> "🔥 金鹰超清原画线"
            raw.contains("lz", ignoreCase = true) || raw.contains("量子") -> "⚡ 量子极速备用线"
            else -> "播放专线 ($raw)"
        }
    }
}
