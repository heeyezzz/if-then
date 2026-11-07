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
    val color: Long = 0xFF2B4CE0,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
