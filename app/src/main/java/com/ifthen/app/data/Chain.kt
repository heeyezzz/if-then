package com.ifthen.app.data

/**
 * 链条不是实体，是顺着 Rule.nextRuleId 指针走出来的路径：一个触发场景后面跟着一串连带动作。
 * 约束：每个节点最多一个前驱、一个后继（严格线性，不分叉不成环）。
 */
object Chain {

    /** 清洗指针：丢弃悬空/自环边，同一点只留一个前驱，多余边砍断以破环 */
    fun sanitizedNext(rules: List<Rule>): Map<Long, Long> {
        val ids = rules.mapTo(HashSet()) { it.id }
        val out = LinkedHashMap<Long, Long>()
        val hasPred = HashSet<Long>()
        for (r in rules) {
            val n = r.nextRuleId ?: continue
            if (n == r.id || n !in ids || n in hasPred) continue
            out[r.id] = n
            hasPred.add(n)
        }
        val visited = HashSet<Long>()
        for (start in out.keys.toList()) {
            if (start in visited) continue
            var cur: Long? = start
            var prev: Long? = null
            while (cur != null) {
                if (!visited.add(cur)) {
                    prev?.let { out.remove(it) }
                    break
                }
                prev = cur
                cur = out[cur]
            }
        }
        return out
    }

    /** 头→尾，只返回长度 >= 2 的链条 */
    fun chains(rules: List<Rule>): List<List<Rule>> {
        val next = sanitizedNext(rules)
        val byId = rules.associateBy { it.id }
        val preds = next.values.toSet()
        return next.keys.filter { it !in preds }.mapNotNull { head ->
            walk(head, next, byId).takeIf { it.size >= 2 }
        }
    }

    /** 从 headId 顺指针走到尾 */
    fun walk(headId: Long, next: Map<Long, Long>, byId: Map<Long, Rule>): List<Rule> {
        val path = ArrayList<Rule>()
        val seen = HashSet<Long>()
        var cur: Long? = headId
        while (cur != null && seen.add(cur)) {
            val r = byId[cur] ?: break
            path.add(r)
            if (path.size > 32) break
            cur = next[cur]
        }
        return path
    }

    /** 把 fromId 的后继设成 toId 会不会成环 */
    fun wouldCycle(fromId: Long, toId: Long, next: Map<Long, Long>): Boolean {
        if (fromId == toId) return true
        var cur: Long? = next[toId]
        var guard = 0
        while (cur != null && guard++ < 64) {
            if (cur == fromId) return true
            cur = next[cur]
        }
        return false
    }

    /** 可作为「下一步」的候选：不能是自己、不能是自己的后代；已有前驱的节点要连前驱一起搬走，所以只放行「无前驱」和「前驱就是我」 */
    fun linkCandidates(rules: List<Rule>, selfId: Long): List<Rule> {
        val next = sanitizedNext(rules)
        val predOf = next.entries.associate { (from, to) -> to to from }
        return rules.filter {
            it.id != selfId &&
                (predOf[it.id] == null || predOf[it.id] == selfId) &&
                !wouldCycle(selfId, it.id, next)
        }
    }
}
