package com.ifthen.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 链的存活状态：活动 / 已归档（归档后成员一起退出首页活动区和复盘统计） */
enum class ChainStatus { ACTIVE, ARCHIVED }

/**
 * 链是真实体：一条有名字、归某个文件夹、能整条归档/删除的有序预案序列。
 * 成员通过 Rule.chainId + Rule.stepIndex 归属，严格线性、不分叉不成环
 * （序号由保存时按行序重排保证，不再有 nextRuleId 指针）。
 */
@Serializable
@Entity(tableName = "chains")
data class Chain(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val folderId: Long? = null,
    val status: String = ChainStatus.ACTIVE.name,
    val color: Int? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

val Chain.isArchived: Boolean get() = status == ChainStatus.ARCHIVED.name

/** 从「已带 chainId/stepIndex 的预案集合」还原出链的分组，供 UI 与业务层只读消费 */
object ChainGraph {

    /** chainId -> 按 stepIndex 升序的成员；忽略无链的散预案 */
    fun orderedMembers(rules: List<Rule>): Map<Long, List<Rule>> =
        rules.filter { it.chainId != null }
            .groupBy { it.chainId!! }
            .mapValues { (_, ms) -> ms.sortedBy { it.stepIndex ?: 0 } }

    /** 只返回长度 >= 2 的链，头 -> 尾 */
    fun paths(rules: List<Rule>): List<List<Rule>> =
        orderedMembers(rules).values.filter { it.size >= 2 }
}
