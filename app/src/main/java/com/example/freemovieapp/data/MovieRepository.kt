package com.example.freemovieapp.data

import com.example.freemovieapp.model.Movie

object MovieRepository {

    // 默认内置的公共领域与开源合规流媒体清单
    private val movieList = listOf(
        Movie(
            id = "1",
            title = "大雄兔 (Big Buck Bunny)",
            category = "动画精选",
            year = "2008",
            rating = 8.5,
            duration = "10 分钟",
            description = "Blender 开源基金会知名动画短片，讲述森林中温和的大兔子智斗三只恶作剧小松鼠的趣味历险故事。",
            posterUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
            backdropUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            isFeatured = true
        ),
        Movie(
            id = "2",
            title = "钢铁之泪 (Tears of Steel)",
            category = "科幻未来",
            year = "2012",
            rating = 8.1,
            duration = "12 分钟",
            description = "在反乌托邦未来的阿姆斯特丹，一群年轻科学家试图通过时空全息技术挽救昔日恋人与即将毁灭的世界。",
            posterUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
            backdropUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            isFeatured = true
        ),
        Movie(
            id = "3",
            title = "辛特尔之龙 (Sintel)",
            category = "奇幻探险",
            year = "2010",
            rating = 8.8,
            duration = "15 分钟",
            description = "流浪女孩 Sintel 在荒漠中救起了一只受重伤的幼龙，当幼龙被成年巨龙掳走后，她踏上了漫长而危险的寻龙复仇之旅。",
            posterUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
            backdropUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            isFeatured = true
        ),
        Movie(
            id = "4",
            title = "大象之梦 (Elephants Dream)",
            category = "科幻未来",
            year = "2006",
            rating = 7.9,
            duration = "11 分钟",
            description = "全球第一部由开源软件制作的 CGI 电影，讲述青年 Emo 与导师 Proog 穿行在巨大荒诞机械世界的哲学思辨之旅。",
            posterUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
            backdropUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
        ),
        Movie(
            id = "5",
            title = "极限越野 (For Bigger Blazes)",
            category = "极限运动",
            year = "2019",
            rating = 8.3,
            duration = "15 秒",
            description = "高清实拍山地越野摩托冲下陡峭险滩的极速瞬间，充满肾上腺素的视觉体验。",
            posterUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg",
            backdropUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        ),
        Movie(
            id = "6",
            title = "宇宙洗衣房 (Cosmos Laundromat)",
            category = "动画精选",
            year = "2015",
            rating = 8.4,
            duration = "12 分钟",
            description = "在一座孤立的海岛上，绝望的绵羊 Franck 遇到了神秘的推销员 Victor，进入了一个连接无数平行宇宙的时空洗衣房。",
            posterUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/WeAreGoingOnBullrun.jpg",
            backdropUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/WeAreGoingOnBullrun.jpg",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4"
        )
    )

    fun getAllMovies(): List<Movie> = movieList

    fun getFeaturedMovies(): List<Movie> = movieList.filter { it.isFeatured }

    fun getCategories(): List<String> = listOf("全部") + movieList.map { it.category }.distinct()

    fun getMoviesByCategory(category: String): List<Movie> {
        return if (category == "全部") movieList else movieList.filter { it.category == category }
    }

    fun getMovieById(id: String): Movie? = movieList.find { it.id == id }

    fun searchMovies(query: String): List<Movie> {
        if (query.isBlank()) return movieList
        return movieList.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true) ||
            it.category.contains(query, ignoreCase = true)
        }
    }
}
