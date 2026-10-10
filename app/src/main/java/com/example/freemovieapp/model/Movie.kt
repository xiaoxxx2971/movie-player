package com.example.freemovieapp.model

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
    val videoUrl: String,
    val isFeatured: Boolean = false
)
