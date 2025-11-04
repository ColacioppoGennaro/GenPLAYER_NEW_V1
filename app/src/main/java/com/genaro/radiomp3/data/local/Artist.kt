package com.genaro.radiomp3.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Artist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val albumCount: Int = 0,
    val trackCount: Int = 0
)