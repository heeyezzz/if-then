package com.ifthen.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "📁",
    val color: Long = 0xFF1F6E5C,
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
