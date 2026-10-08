package com.ifthen.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class RuleStatus { ACTIVE, ARCHIVED, DONE, MISSED }

/** 一次性＝情境只来一次，做完/没做即结清；长期＝反复照案执行的习惯清单 */
enum class RuleKind { ONE_OFF, REPEATING }

@Serializable
@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ifText: String,
    val thenText: String,
    // 两分钟退化版：状态差时只做这一句也算「做了」
    val miniThen: String = "",
    val tags: String = "",
    // v1 预留：将来自动触发的配置（JSON），暂不实现
    val triggerConfig: String? = null,
    val folderId: Long? = null,
    val sortOrder: Int = 0,
    // 链条：做完这条之后接哪条。null = 这条不是任何链条的中间节点
    val nextRuleId: Long? = null,
    val kind: String = RuleKind.REPEATING.name,
    // 一次性预案的可选到期日（本地当天 0 点毫秒）；只用于提醒，到期不自动改状态
    val dueDate: Long? = null,
    val status: String = RuleStatus.ACTIVE.name,
    val createdAt: Long = System.currentTimeMillis()
)

val Rule.isActive: Boolean get() = status == RuleStatus.ACTIVE.name
val Rule.isOneOff: Boolean get() = kind == RuleKind.ONE_OFF.name
val Rule.isResolved: Boolean get() = status == RuleStatus.DONE.name || status == RuleStatus.MISSED.name
