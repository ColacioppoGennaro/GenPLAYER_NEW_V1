package com.genaro.radiomp3.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val title: String?,
    val artistName: String?,
    val albumTitle: String?,
    val albumIdRef: Long?,
    val durationMs: Long?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val bitDepth: Int?,
    val channels: Int?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val dateAdded: Long?,
    val folderPathDisplay: String?,
    val embeddedArtHash: String?,
    val audioHash: String?,
    val source: String
)