package com.genaro.radiomp3.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Album(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artistName: String?,
    val year: Int?,
    val artEmbeddedHash: String?,
    val trackCount: Int = 0
)