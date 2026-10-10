package com.example.freemovieapp.data

import com.example.freemovieapp.model.Episode
import com.example.freemovieapp.model.Movie
import com.example.freemovieapp.model.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MovieRepository {

    // 默认内置高带宽 1080P 高清接口
    var currentApiEndpoint = "https://cj.lziapi.com/api.php/provide/vod/"
    private val backupApiEndpoints = listOf(
        "https://api.bfzyapi.com/api.php/provide/vod/",
        "https://api.wujinapi.me/api.php/provide/vod/",
        "https://caiji.dbzy.tv/api.php/provide/vod/at/json/"
    )

    // 内存缓存：返回首页或搜索时 0 延迟秒开，避免重复转圈
    var cachedHomeMovies: List<Movie> = emptyList()
    var cachedSearchResults: List<Movie> = emptyList()
    var lastSearchQuery: String = ""

    private val movieDetailsCache = mutableMapOf<String, Movie>()

    // 本地 4K/1080P 超清演示片单
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
                VideoSource("1080P 极速蓝光线", listOf(
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
                VideoSource("1080P 原画线", listOf(
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

    // 获取最新热门影视流（支持内存秒回，避免返回时转圈）
    suspend fun fetchLatestMovies(forceRefresh: Boolean = false): List<Movie> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedHomeMovies.isNotEmpty()) {
            return@withContext cachedHomeMovies
        }
        val url = "$currentApiEndpoint?ac=detail&pg=1"
        val movies = requestApi(url)
        val result = if (movies.isEmpty()) fallbackMovies else movies
        cachedHomeMovies = result
        result
    }

    // 线上全网搜索影视（带搜索缓存）
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

                        // 解析多线路与剧集，优先置顶 1080P / 高清蓝光线路
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
                                description = content.ifBlank { "全网精彩热播高清影视，点击立即观看。" },
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

    // 解析出全部线路并智能排序（高清蓝光线排在最前）
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
                        episodes.add(Episode(name = "全片 1080P", url = epUrl))
                    }
                }
            }
            if (episodes.isNotEmpty()) {
                sourceList.add(VideoSource(formatSourceName(fromName), episodes))
            }
        }

        // 优先级排序：优先包含 1080、m3u8、蓝光、超清的线路
        return sourceList.sortedByDescending { src ->
            var score = 0
            if (src.name.contains("1080", ignoreCase = true)) score += 50
            if (src.name.contains("蓝光") || src.name.contains("超清")) score += 40
            if (src.name.contains("量子") || src.name.contains("暴风") || src.name.contains("非凡")) score += 30
            if (src.episodes.any { it.url.contains(".m3u8") }) score += 20
            score
        }
    }

    private fun formatSourceName(raw: String): String {
        return when {
            raw.contains("1080", ignoreCase = true) -> "💎 1080P 超清蓝光线"
            raw.contains("lz", ignoreCase = true) || raw.contains("量子") -> "⚡ 量子极速高清线"
            raw.contains("bf", ignoreCase = true) || raw.contains("暴风") -> "🚀 暴风 1080P 专线"
            raw.contains("ff", ignoreCase = true) || raw.contains("非凡") -> "🔥 非凡高速画质线"
            raw.contains("wj", ignoreCase = true) || raw.contains("无尽") -> "🌟 无尽高清播放线"
            else -> "播放线路 ($raw)"
        }
    }
}
