package com.example.finalproject.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Game(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val tags: List<String> = emptyList(),
    val rating: Double? = null
) : Parcelable
