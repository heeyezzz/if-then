package com.ifthen.app.ui

import com.ifthen.app.Constants
import com.ifthen.app.data.Chain
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

/** 链条健康：近 14 天走通情况 + 最常断在第几步 */
data class ChainHealth(
    val headId: Long,
    val title: String,
    val size: Int,
    val activeDays: Int,
    val fullDays: Int,
    val breakAt: List<Int>,
    val worstStep: Int
)

data class ChainSnapshot(
    val chainPos: Map<Long, ChainPos> = emptyMap(),
    val groups: List<RuleGroup> = emptyList(),
    val todayByHead: Map<Long, ChainToday> = emptyMap(),
    val nextStep: NextStep? = null,
    val health: List<ChainHealth> = emptyList()
)

object ChainState {

    fun compute(
        allActive: List<Rule>,
        inFolder: List<Rule>,
        logs: List<ExecLog>,
        todayStart: Long
    ): ChainSnapshot {
        val chains = Chain.chains(allActive)
        if (chains.isEmpty()) {
            return ChainSnapshot(groups = inFolder.map { RuleGroup(listOf(it)) })
        }
        val pos = HashMap<Long, ChainPos>()
        chains.forEach { path ->
            path.forEachIndexed { i, r -> pos[r.id] = ChainPos(path.first().id, i + 1, path.size) }
        }

        val today = logs.filter { it.createdAt >= todayStart }
        val doneToday = HashSet<Long>()
        val resolvedToday = HashSet<Long>()
        today.forEach { l ->
            if (l.done == true) doneToday.add(l.ruleId)
            if (l.done != null) resolvedToday.add(l.ruleId)
        }

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
            val lastDone = today.filter { it.done == true && path.any { r -> r.id == it.ruleId } }
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
            groups = groupsOf(inFolder, allActive),
            todayByHead = todayByHead,
            nextStep = best,
            health = healthOf(chains, logs, todayStart)
        )
    }

    /** 同一文件夹视图内按指针聚成组；异夹后继退化成角标 */
    fun groupsOf(inFolder: List<Rule>, allActive: List<Rule>): List<RuleGroup> {
        if (inFolder.none { it.nextRuleId != null }) return inFolder.map { RuleGroup(listOf(it)) }
        val next = Chain.sanitizedNext(allActive)
        val byId = allActive.associateBy { it.id }
        val here = inFolder.mapTo(HashSet()) { it.id }
        val consumed = HashSet<Long>()
        val out = ArrayList<RuleGroup>()
        for (start in inFolder) {
            if (start.id in consumed) continue
            val pred = allActive.firstOrNull { next[it.id] == start.id }
            if (pred != null && pred.id in here) continue
            val path = ArrayList<Rule>()
            val extras = ArrayList<Rule>()
            var id: Long? = start.id
            var guard = 0
            while (id != null && guard++ < Constants.CHAIN_MAX_STEPS) {
                val node = byId[id] ?: break
                if (node.id in here) {
                    if (node.id in consumed) break
                    path.add(node)
                    consumed.add(node.id)
                    id = next[node.id]
                } else {
                    extras.add(node)
                    break
                }
            }
            if (path.isNotEmpty()) out.add(RuleGroup(path, extras))
        }
        // 指针异常（成环被破边）时兜底：没被任何组收下的预案各自成组，避免首页丢条目
        val shown = out.flatMap { it.rules }.mapTo(HashSet()) { it.id }
        inFolder.forEach { if (it.id !in shown) out.add(RuleGroup(listOf(it))) }
        return out
    }

    private fun healthOf(
        chains: List<List<Rule>>,
        logs: List<ExecLog>,
        todayStart: Long
    ): List<ChainHealth> {
        val dayMs = 24L * 3600_000
        val out = ArrayList<ChainHealth>()
        chains.forEach { path ->
            val breakAt = IntArray(path.size)
            var activeDays = 0
            var fullDays = 0
            for (d in 0 until Constants.CHAIN_HEALTH_DAYS) {
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
            if (activeDays == 0) return@forEach
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
                    worstStep = worstIdx + 1
                )
            )
        }
        return out.sortedByDescending { it.activeDays }
    }
}
