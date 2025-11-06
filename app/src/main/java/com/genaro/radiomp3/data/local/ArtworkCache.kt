package com.genaro.radiomp3.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ArtworkCache(
    @PrimaryKey val hash: String,
    val path: String,
    val width: Int?,
    val height: Int?
)