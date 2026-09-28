package com.ifthen.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * done 三态：true=触发且做了，false=触发但没做，null=待确认（晚上补记）
 */
@Serializable
@Entity(tableName = "exec_logs")
data class ExecLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val done: Boolean? = null,
    val createdAt: Long = System.currentTimeMillis()
)
