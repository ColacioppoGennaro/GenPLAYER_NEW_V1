package com.genaro.radiomp3.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SafRoot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val displayName: String?,
    val takeFlags: Int
)