package com.example.freemovieapp.data

import com.example.freemovieapp.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MovieRepository {

    // 默认内置的公开多线路影视 API
    var currentApiEndpoint = "https://cj.lziapi.com/api.php/provide/vod/"
    private val backupApiEndpoints = listOf(
        "https://api.wujinapi.me/api.php/provide/vod/",
        "https://caiji.dbzy.tv/api.php/provide/vod/at/json/"
    )

    // 缓存数据供详情页获取
    private val movieCache = mutableMapOf<String, Movie>()

    // 本地高质量兜底片单（确保离线或弱网下也有内容展示）
    private val fallbackMovies = listOf(
        Movie(
            id = "fb_1",
            title = "大雄兔 (Big Buck Bunny)",
            category = "动画",
            year = "2008",
            rating = 8.5,
            duration = "10 分钟",
            description = "Blender 开源基金会经典动画短片，讲述森林中温和的大兔子智斗三只恶作剧松鼠的幽默故事。",
            posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1000&q=80",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            isFeatured = true
        ),
        Movie(
            id = "fb_2",
            title = "钢铁之泪 (Tears of Steel)",
            category = "科幻",
            year = "2012",
            rating = 8.1,
            duration = "12 分钟",
            description = "在反乌托邦未来的阿姆斯特丹，一群科学家试图通过时空全息技术挽救昔日恋人与毁灭的世界。",
            posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1000&q=80",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            isFeatured = true
        ),
        Movie(
            id = "fb_3",
            title = "辛特尔之龙 (Sintel)",
            category = "奇幻",
            year = "2010",
            rating = 8.8,
            duration = "15 分钟",
            description = "流浪女孩 Sintel 荒漠救下幼龙，幼龙被掳走后她踏上漫长艰险的寻龙复仇之旅。",
            posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1000&q=80",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            isFeatured = true
        )
    )

    init {
        fallbackMovies.forEach { movieCache[it.id] = it }
    }

    fun getMovieById(id: String): Movie? = movieCache[id]

    // 从网络获取最新热播影视
    suspend fun fetchLatestMovies(page: Int = 1): List<Movie> = withContext(Dispatchers.IO) {
        val url = "$currentApiEndpoint?ac=detail&pg=$page"
        val movies = requestApi(url)
        if (movies.isEmpty()) {
            fallbackMovies
        } else {
            movies
        }
    }

    // 线上全网搜索影视
    suspend fun searchOnlineMovies(keyword: String): List<Movie> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) {
            return@withContext fetchLatestMovies()
        }
        val encodedWd = URLEncoder.encode(keyword.trim(), "UTF-8")
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

        if (results.isEmpty()) {
            fallbackMovies.filter { it.title.contains(keyword, ignoreCase = true) }
        } else {
            results
        }
    }

    private fun requestApi(urlString: String): List<Movie> {
        val results = mutableListOf<Movie>()
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)")
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
                        val playUrlRaw = item.optString("vod_play_url", "")

                        val videoUrl = parsePlayUrl(playUrlRaw)
                        if (videoUrl.isNotBlank()) {
                            val movie = Movie(
                                id = id,
                                title = title,
                                category = typeName,
                                year = year,
                                rating = 8.5,
                                duration = remarks,
                                description = content.ifBlank { "全网精彩热播高清影视，点击立即观看。" },
                                posterUrl = pic,
                                backdropUrl = pic,
                                videoUrl = videoUrl,
                                isFeatured = (i == 0)
                            )
                            results.add(movie)
                            movieCache[id] = movie
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

    private fun parsePlayUrl(raw: String): String {
        if (raw.isBlank()) return ""
        val episodes = raw.split("#")
        for (ep in episodes) {
            val parts = ep.split("$")
            val url = if (parts.size > 1) parts[1].trim() else parts[0].trim()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                if (url.contains(".m3u8") || url.contains(".mp4")) {
                    return url
                }
            }
        }
        for (ep in episodes) {
            val parts = ep.split("$")
            val url = if (parts.size > 1) parts[1].trim() else parts[0].trim()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url
            }
        }
        return ""
    }
}
