package com.example.finalproject

import com.example.finalproject.models.Game

data class MyGameItem(
    val game: Game,
    val progress: String,
    val userRating: Double? = null
)
