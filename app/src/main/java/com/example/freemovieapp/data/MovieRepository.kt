package com.example.freemovieapp.data

import com.example.freemovieapp.model.Episode
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

    // 默认内置高质量公开影视接口（优先 1080P 高清切片流）
    var currentApiEndpoint = "https://cj.lziapi.com/api.php/provide/vod/"
    private val backupApiEndpoints = listOf(
        "https://api.bfzyapi.com/api.php/provide/vod/",
        "https://api.wujinapi.me/api.php/provide/vod/",
        "https://caiji.dbzy.tv/api.php/provide/vod/at/json/"
    )

    private val movieCache = mutableMapOf<String, Movie>()

    // 本地高清兜底片单
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
            episodes = listOf(
                Episode("正片 1080P", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4")
            ),
            isFeatured = true
        )
    )

    init {
        fallbackMovies.forEach { movieCache[it.id] = it }
    }

    fun getMovieById(id: String): Movie? = movieCache[id]

    suspend fun fetchLatestMovies(page: Int = 1): List<Movie> = withContext(Dispatchers.IO) {
        val url = "$currentApiEndpoint?ac=detail&pg=$page"
        val movies = requestApi(url)
        if (movies.isEmpty()) fallbackMovies else movies
    }

    suspend fun searchOnlineMovies(keyword: String): List<Movie> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext fetchLatestMovies()
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
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; Mobile)")
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

                        // 解析所有剧集（多集、全集支持）
                        val episodes = parseEpisodes(playUrlRaw)
                        if (episodes.isNotEmpty()) {
                            val movie = Movie(
                                id = id,
                                title = title,
                                category = typeName,
                                year = year,
                                rating = 8.8,
                                duration = remarks,
                                description = content.ifBlank { "高清精选影视，多集在线极速播放。" },
                                posterUrl = pic,
                                backdropUrl = pic,
                                episodes = episodes,
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

    // 解析出完整的剧集列表（例如 第01集、第02集...）
    private fun parseEpisodes(playUrlRaw: String): List<Episode> {
        if (playUrlRaw.isBlank()) return emptyList()
        val list = mutableListOf<Episode>()

        // 多个播放源以 $$$ 分割，优先选取包含 m3u8 的高清源
        val sources = playUrlRaw.split("$$$")
        val bestSource = sources.firstOrNull { it.contains(".m3u8", ignoreCase = true) } ?: sources.first()

        val episodePairs = bestSource.split("#")
        for ((index, item) in episodePairs.withIndex()) {
            val parts = item.split("$")
            if (parts.size >= 2) {
                val epName = parts[0].trim()
                val epUrl = parts[1].trim()
                if (epUrl.startsWith("http://") || epUrl.startsWith("https://")) {
                    list.add(Episode(name = if (epName.isBlank()) "第 ${index + 1} 集" else epName, url = epUrl))
                }
            } else if (parts.size == 1) {
                val epUrl = parts[0].trim()
                if (epUrl.startsWith("http://") || epUrl.startsWith("https://")) {
                    list.add(Episode(name = "正片 1080P", url = epUrl))
                }
            }
        }
        return list
    }
}
