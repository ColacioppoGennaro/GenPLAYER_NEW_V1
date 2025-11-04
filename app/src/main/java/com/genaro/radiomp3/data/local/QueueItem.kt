package com.genaro.radiomp3.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class QueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val position: Int,
    val addedAt: Long
)
