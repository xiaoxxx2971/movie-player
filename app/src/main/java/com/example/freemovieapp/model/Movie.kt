package com.example.freemovieapp.model

data class Episode(
    val name: String,
    val url: String
)

data class Movie(
    val id: String,
    val title: String,
    val category: String,
    val year: String,
    val rating: Double,
    val duration: String,
    val description: String,
    val posterUrl: String,
    val backdropUrl: String,
    val episodes: List<Episode>,
    val isFeatured: Boolean = false
) {
    val defaultVideoUrl: String
        get() = episodes.firstOrNull()?.url ?: ""
}
