package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val isFavorite: Boolean = false,
    val mimeType: String = "audio/mpeg",
    val folder: String = "الأغاني",
    val isBuiltIn: Boolean = false
)
