package com.ifthen.app.ui

import com.ifthen.app.Constants
import com.ifthen.app.data.ChainGraph
import com.ifthen.app.data.ExecLog
import com.ifthen.app.data.Rule

/** 节点在链条里的位置：头 id、第几步（1 起）、链长 */
data class ChainPos(val headId: Long, val step: Int, val size: Int)

/** 首页聚合后的一个渲染单元：单条预案 = 只含一个元素的 rules */
data class RuleGroup(val rules: List<Rule>, val offFolderTails: List<Rule> = emptyList())

/** 今日该链条的推进状态 */
data class ChainToday(val size: Int, val doneToday: Int)

/** 顶部「下一步」提示条 */
data class NextStep(val rule: Rule, val prevIfText: String, val headId: Long, val step: Int, val size: Int)

/** 链条健康：窗口内走通情况 + 最常断在第几步 */
data class ChainHealth(
    val headId: Long,
    val title: String,
    val size: Int,
    val activeDays: Int,
    val fullDays: Int,
    val breakAt: List<Int>,
    val worstStep: Int,
    /** 每一步对应的预案 id，结论卡点「断掉的那步」要跳去编辑它 */
    val stepRuleIds: List<Long>
)

data class ChainSnapshot(
    val chainPos: Map<Long, ChainPos> = emptyMap(),
    val groups: List<RuleGroup> = emptyList(),
    val todayByHead: Map<Long, ChainToday> = emptyMap(),
    val nextStep: NextStep? = null,
    /** 实际算了多少天，UI 用它标口径，避免「全部」档下写死天数 */
    val healthDays: Int = Constants.CHAIN_HEALTH_DAYS,
    val health: List<ChainHealth> = emptyList()
)

object ChainState {

    fun compute(
        allActive: List<Rule>,
        inFolder: List<Rule>,
        logs: List<ExecLog>,
        todayStart: Long,
        healthDays: Int
    ): ChainSnapshot {
        val chains = ChainGraph.paths(allActive)
        if (chains.isEmpty()) {
            return ChainSnapshot(groups = inFolder.map { RuleGroup(listOf(it)) })
        }
        val pos = HashMap<Long, ChainPos>()
        chains.forEach { path ->
            path.forEachIndexed { i, r -> pos[r.id] = ChainPos(path.first().id, i + 1, path.size) }
        }

        val today = logs.filter { it.createdAt >= todayStart }
        // 以今天最新一条记录为准：后一次打卡覆盖前一次的口径
        val latestToday = HashMap<Long, ExecLog>()
        today.forEach { l ->
            val prev = latestToday[l.ruleId]
            if (prev == null || l.createdAt > prev.createdAt) latestToday[l.ruleId] = l
        }
        val doneToday = latestToday.filterValues { it.done == true }.keys
        val resolvedToday = latestToday.filterValues { it.done != null }.keys

        val todayByHead = HashMap<Long, ChainToday>()
        chains.forEach { path ->
            val head = path.first().id
            todayByHead[head] = ChainToday(
                size = path.size,
                doneToday = path.count { it.id in doneToday }
            )
        }

        // 提示条：链条从头部推进后，第一个今天还没定论的节点
        var best: NextStep? = null
        var bestAt = 0L
        chains.forEach { path ->
            val head = path.first()
            if (head.id !in doneToday) return@forEach
            val idx = path.indexOfFirst { it.id !in resolvedToday }
            if (idx < 0) return@forEach
            val lastDone = latestToday.values
                .filter { it.done == true && path.any { r -> r.id == it.ruleId } }
                .maxOfOrNull { it.createdAt } ?: 0L
            if (lastDone > bestAt) {
                bestAt = lastDone
                best = NextStep(
                    rule = path[idx],
                    prevIfText = path[idx - 1].ifText,
                    headId = head.id,
                    step = idx + 1,
                    size = path.size
                )
            }
        }

        return ChainSnapshot(
            chainPos = pos,
            groups = groupsOf(inFolder),
            todayByHead = todayByHead,
            nextStep = best,
            healthDays = healthDays,
            health = healthOf(chains, logs, todayStart, healthDays)
        )
    }

    /**
     * 文件夹视图内的渲染分组：链（成员>=2）聚成一组，其余退成单条。
     * 链是单夹实体，成员都带同一个 chainId，所以整串都落在这个文件夹里。
     */
    fun groupsOf(inFolder: List<Rule>): List<RuleGroup> {
        val out = ArrayList<RuleGroup>()
        val consumed = HashSet<Long>()
        inFolder.asSequence()
            .filter { it.chainId != null }
            .groupBy { it.chainId!! }
            .mapValues { (_, ms) -> ms.sortedBy { it.stepIndex ?: 0 } }
            .values
            .forEach { members ->
                if (members.size >= 2) {
                    out.add(RuleGroup(members, emptyList()))
                    members.forEach { consumed.add(it.id) }
                }
            }
        // 没被任何链收下的（散预案 + 掉到 1 的残链）各自成组，保证首页不丢条目
        inFolder.forEach { if (it.id !in consumed) out.add(RuleGroup(listOf(it))) }
        return out
    }

    private fun healthOf(
        chains: List<List<Rule>>,
        logs: List<ExecLog>,
        todayStart: Long,
        days: Int
    ): List<ChainHealth> {
        val dayMs = 24L * 3600_000
        val out = ArrayList<ChainHealth>()
        chains.forEach { path ->
            val breakAt = IntArray(path.size)
            var activeDays = 0
            var fullDays = 0
            for (d in 0 until days) {
                val d0 = todayStart - d * dayMs
                val d1 = d0 + dayMs
                val dayLogs = logs.filter { it.createdAt >= d0 && it.createdAt < d1 }
                val touched = path.count { r -> dayLogs.any { it.ruleId == r.id } }
                if (touched == 0) continue
                activeDays++
                if (path.all { r -> dayLogs.any { it.ruleId == r.id && it.done == true } }) fullDays++
                if (dayLogs.any { it.ruleId == path.first().id && it.done == true }) {
                    val idx = path.indexOfFirst { r -> dayLogs.any { it.ruleId == r.id && it.done == false } }
                    if (idx >= 0) breakAt[idx]++
                }
            }
            // 短窗口里活跃一两天就算不出「最常断在哪」，宁可不说话也别给假结论
            if (activeDays < Constants.CHAIN_HEALTH_MIN_ACTIVE_DAYS) return@forEach
            val worstIdx = if (breakAt.all { it == 0 }) -1
            else breakAt.indices.maxByOrNull { breakAt[it] } ?: -1
            out.add(
                ChainHealth(
                    headId = path.first().id,
                    title = path.first().ifText,
                    size = path.size,
                    activeDays = activeDays,
                    fullDays = fullDays,
                    breakAt = breakAt.toList(),
                    worstStep = worstIdx + 1,
                    stepRuleIds = path.map { it.id }
                )
            )
        }
        return out.sortedByDescending { it.activeDays }
    }
}
