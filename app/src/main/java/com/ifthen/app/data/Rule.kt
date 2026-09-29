package com.ifthen.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class RuleStatus { ACTIVE, ARCHIVED }

@Serializable
@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ifText: String,
    val thenText: String,
    val tags: String = "",
    // v1 预留：将来自动触发的配置（JSON），暂不实现
    val triggerConfig: String? = null,
    val folderId: Long? = null,
    val status: String = RuleStatus.ACTIVE.name,
    val createdAt: Long = System.currentTimeMillis()
)

val Rule.isActive: Boolean get() = status == RuleStatus.ACTIVE.name
