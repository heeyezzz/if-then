package com.ifthen.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ifthen.app.Constants
import com.ifthen.app.ai.AiCandidate
import com.ifthen.app.ai.DeepSeekClient
import com.ifthen.app.data.ApiSettings
import com.ifthen.app.data.AppDatabase
import com.ifthen.app.data.BackupRepository
import com.ifthen.app.data.Chain
import com.ifthen.app.data.ExecLog
import com.ifthen.app.data.Folder
import com.ifthen.app.data.Rule
import com.ifthen.app.data.RuleKind
import com.ifthen.app.data.RuleStatus
import com.ifthen.app.data.isOneOff
import com.ifthen.app.data.isResolved
import com.ifthen.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class RuleStat(
    val streak: Int = 0,
    val triggers30: Int = 0,
    val done30: Int = 0,
    val lastTriggerAt: Long? = null
)

/** 一条预案「今天」的状态：以今天最新一条记录为准 */
enum class TodayState { NONE, DONE, MISSED, PENDING }

data class TodayStatus(
    val state: TodayState,
    val at: Long = 0L,
    val mini: Boolean = false,
    val backfill: Boolean = false
)

data class FolderSummary(
    val folder: Folder,
    val depth: Int,
    val triggers30: Int,
    val done30: Int
)

data class OneOffStats(
    val done: Int,
    val missed: Int,
    val pending: Int,
    val overdue: Int
) {
    val rate: Int? get() = if (done + missed == 0) null else done * 100 / (done + missed)
}

data class AppUiState(
    val activeRules: List<Rule> = emptyList(),
    val archivedRules: List<Rule> = emptyList(),
    val oneOffActive: List<Rule> = emptyList(),
    val oneOffResolvedToday: List<Rule> = emptyList(),
    val oneOffOverdueDays: Map<Long, Int> = emptyMap(),
    val miniDone30: Int = 0,
    val totalDone30: Int = 0,
    val oneOffStats: OneOffStats = OneOffStats(0, 0, 0, 0),
    val folders: List<Folder> = emptyList(),
    val currentFolderId: Long? = null,
    val crumbs: List<Folder> = emptyList(),
    val visibleFolders: List<Folder> = emptyList(),
    val visibleRules: List<Rule> = emptyList(),
    val frequentRuleIds: List<Long> = emptyList(),
    val stats: Map<Long, RuleStat> = emptyMap(),
    val todayStatusByRule: Map<Long, TodayStatus> = emptyMap(),
    val folderSummaries: List<FolderSummary> = emptyList(),
    val todayLogs: List<ExecLog> = emptyList(),
    val heatmap: List<Int> = List(Constants.HEATMAP_DAYS) { 0 },
    val zombies: List<Rule> = emptyList(),
    val highTriggers: List<Rule> = emptyList(),
    val ruleById: Map<Long, Rule> = emptyMap(),
    val chain: ChainSnapshot = ChainSnapshot()
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val settingsRepo = SettingsRepository(app)
    private val backupRepo = BackupRepository(app)
    private val aiClient = DeepSeekClient()

    val apiSettings: StateFlow<ApiSettings> = settingsRepo.apiSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiSettings("", "", ""))

    private val now = MutableStateFlow(System.currentTimeMillis())
    private val currentFolderId = MutableStateFlow<Long?>(null)

    val ui: StateFlow<AppUiState> = combine(
        db.ruleDao().observeAll(),
        db.execLogDao().observeSince(0L),
        db.folderDao().observeAll(),
        combine(now, currentFolderId) { a, b -> a to b }
    ) { rules, logs, folders, extra ->
        buildState(rules, logs, folders, extra.first, extra.second)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppUiState())

    init {
        viewModelScope.launch {
            while (true) {
                now.value = System.currentTimeMillis()
                kotlinx.coroutines.delay(60_000)
            }
        }
    }

    private fun buildState(
        rules: List<Rule>,
        logs: List<ExecLog>,
        folders: List<Folder>,
        ts: Long,
        folderId: Long?
    ): AppUiState {
        val activeAll = rules.filter { it.status == RuleStatus.ACTIVE.name }
        // 长期清单才是"战绩/链条/文件夹"的主场；一次性另成一页
        val active = activeAll.filterNot { it.isOneOff }
        val archived = rules.filter { it.status == RuleStatus.ARCHIVED.name }
        val byRule = logs.groupBy { it.ruleId }
        val dayMs = 24L * 3600_000
        val since30 = ts - Constants.RULE_STATS_WINDOW_DAYS * dayMs
        val sinceFrequent = ts - Constants.FREQUENT_WINDOW_DAYS * dayMs

        val stats = active.associate { r ->
            val rl = byRule[r.id].orEmpty()
            var streak = 0
            for (l in rl) {
                when (l.done) {
                    true -> streak++
                    false -> break
                    null -> {}
                }
            }
            val in30 = rl.filter { it.createdAt >= since30 }
            r.id to RuleStat(
                streak = streak,
                triggers30 = in30.size,
                done30 = in30.count { it.done == true },
                lastTriggerAt = rl.firstOrNull()?.createdAt
            )
        }

        val frequent = active
            .mapNotNull { r ->
                val cnt = byRule[r.id].orEmpty().count { it.createdAt >= sinceFrequent }
                if (cnt > 0) r to cnt else null
            }
            .sortedByDescending { it.second }
            .take(Constants.HOME_FREQUENT_LIMIT)
            .map { it.first.id }

        val zombieCutoff = ts - Constants.ZOMBIE_DAYS * dayMs
        val zombies = active.filter { r ->
            r.createdAt < zombieCutoff && (stats[r.id]?.lastTriggerAt ?: 0L) < zombieCutoff
        }
        val high = active
            .filter { (stats[it.id]?.triggers30 ?: 0) >= 3 }
            .sortedByDescending { stats[it.id]?.triggers30 ?: 0 }
            .take(5)

        val todayStart = dayStart(ts)
        val todayLogs = logs.filter { it.createdAt >= todayStart }

        // 今天最新一条记录决定这条预案今天的显示状态
        val todayStatusByRule = HashMap<Long, TodayStatus>()
        todayLogs.forEach { l ->
            val prev = todayStatusByRule[l.ruleId]
            if (prev == null || l.createdAt > prev.at) {
                todayStatusByRule[l.ruleId] = TodayStatus(
                    state = when (l.done) {
                        true -> TodayState.DONE
                        false -> TodayState.MISSED
                        null -> TodayState.PENDING
                    },
                    at = l.createdAt,
                    mini = l.mini,
                    backfill = l.backfill
                )
            }
        }

        // 一次性：未逾期按手动顺序，逾期统一沉底（刚逾期的排在沉底区上方，更可能还来得及）
        val oneOffAll = activeAll.filter { it.isOneOff }
        val onTime = oneOffAll.filter { it.dueDate == null || it.dueDate!! >= todayStart }.sortedBy { it.sortOrder }
        val late = oneOffAll.filter { it.dueDate != null && it.dueDate!! < todayStart }
            .sortedByDescending { it.dueDate }
        val resolvedOneOff = rules.filter { it.isOneOff && it.isResolved }
        val oneOffResolvedToday = resolvedOneOff
            .mapNotNull { r -> byRule[r.id]?.firstOrNull()?.let { l -> r to l } }
            .filter { it.second.createdAt >= todayStart }
            .sortedByDescending { it.second.createdAt }
            .map { it.first }
        val oneOffWindow = ts - Constants.RULE_STATS_WINDOW_DAYS * dayMs
        var od = 0
        var om = 0
        resolvedOneOff.forEach { r ->
            val l = byRule[r.id]?.firstOrNull() ?: return@forEach
            if (l.createdAt < oneOffWindow) return@forEach
            if (r.status == RuleStatus.DONE.name) od++ else om++
        }
        val oneOffStats = OneOffStats(od, om, oneOffAll.size, late.size)
        val done30Logs = logs.filter { it.done == true && it.createdAt >= since30 }
        val heat = ArrayList<Int>(Constants.HEATMAP_DAYS)
        for (i in Constants.HEATMAP_DAYS - 1 downTo 0) {
            val d0 = todayStart - i * dayMs
            val d1 = d0 + dayMs
            heat.add(logs.count { it.done == true && it.createdAt >= d0 && it.createdAt < d1 })
        }

        val childrenOf = folders.groupBy { it.parentId }
        val crumbs = ArrayList<Folder>()
        run {
            var cur = folderId?.let { id -> folders.firstOrNull { it.id == id } }
            val rev = ArrayList<Folder>()
            while (cur != null) {
                rev.add(cur)
                val pid = cur.parentId
                cur = pid?.let { p -> folders.firstOrNull { it.id == p } }
                if (rev.size > 20) break
            }
            crumbs.addAll(rev.reversed())
        }
        val visibleFolders = childrenOf[folderId].orEmpty()
        val visibleRules = active.filter { it.folderId == folderId }

        val summaries = ArrayList<FolderSummary>()
        fun walk(parentId: Long?, depth: Int) {
            childrenOf[parentId].orEmpty().forEach { f ->
                val memberRules = active.filter { it.folderId == f.id }
                summaries.add(
                    FolderSummary(
                        folder = f,
                        depth = depth,
                        triggers30 = memberRules.sumOf { stats[it.id]?.triggers30 ?: 0 },
                        done30 = memberRules.sumOf { stats[it.id]?.done30 ?: 0 }
                    )
                )
                walk(f.id, depth + 1)
            }
        }
        walk(null, 0)

        val chain = ChainState.compute(
            allActive = active,
            inFolder = visibleRules,
            logs = logs,
            todayStart = todayStart
        )

        return AppUiState(
            activeRules = active,
            archivedRules = archived,
            oneOffActive = onTime + late,
            oneOffResolvedToday = oneOffResolvedToday,
            oneOffOverdueDays = late.associate { it.id to ((todayStart - it.dueDate!!) / dayMs).toInt() + 1 },
            miniDone30 = done30Logs.count { it.mini },
            totalDone30 = done30Logs.size,
            oneOffStats = oneOffStats,
            folders = folders,
            currentFolderId = folderId,
            crumbs = crumbs,
            visibleFolders = visibleFolders,
            visibleRules = visibleRules,
            frequentRuleIds = frequent,
            stats = stats,
            todayStatusByRule = todayStatusByRule,
            folderSummaries = summaries,
            todayLogs = todayLogs,
            heatmap = heat,
            zombies = zombies,
            highTriggers = high,
            ruleById = rules.associateBy { it.id },
            chain = chain
        )
    }

    private fun dayStart(ts: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    // ---- 打卡动作 ----

    /**
     * 链条今天全做齐时的一次性庆祝事件（头部 id），1.5 秒后自动清空。
     * 只在实时打卡时触发；晚间补记走 resolveLog，永远静默。
     */
    private val _celebrate = MutableStateFlow<Long?>(null)
    val celebrate: StateFlow<Long?> = _celebrate

    fun logDone(ruleId: Long, mini: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        db.execLogDao().insert(ExecLog(ruleId = ruleId, done = true, mini = mini))
        resolveOneOff(ruleId, true)
        val head = chainCompletedHead(ruleId) ?: return@launch
        SuccessChime.playChain()
        _celebrate.value = head
        launch {
            kotlinx.coroutines.delay(1500)
            if (_celebrate.value == head) _celebrate.value = null
        }
    }

    fun logNotDone(ruleId: Long) = viewModelScope.launch(Dispatchers.IO) {
        db.execLogDao().insert(ExecLog(ruleId = ruleId, done = false))
        resolveOneOff(ruleId, false)
    }

    /** 一次性预案打完即结清：情境只来一次，做了/没做都当场落定 */
    private suspend fun resolveOneOff(ruleId: Long, done: Boolean) {
        val r = db.ruleDao().getById(ruleId) ?: return
        if (!r.isOneOff) return
        db.ruleDao().update(
            r.copy(status = if (done) RuleStatus.DONE.name else RuleStatus.MISSED.name)
        )
    }

    /** 反悔：把结清的一次性预案挂回清单。打卡记录保留——它确实发生过 */
    fun reopenRule(ruleId: Long, onResult: (String) -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        val r = db.ruleDao().getById(ruleId) ?: return@launch
        db.ruleDao().update(r.copy(status = RuleStatus.ACTIVE.name))
        kotlinx.coroutines.withContext(Dispatchers.Main) { onResult("已重新挂回一次性清单") }
    }

    /** 改类型：转一次性时若正卡在链条里，自动把它摘出去（前后直接接上） */
    fun convertKind(ruleId: Long, toOneOff: Boolean, onResult: (String) -> Unit = {}) =
        viewModelScope.launch(Dispatchers.IO) {
            val r = db.ruleDao().getById(ruleId) ?: return@launch
            val msg = if (toOneOff) {
                val splice = spliceOut(ruleId)
                db.ruleDao().update(
                    r.copy(kind = RuleKind.ONE_OFF.name, nextRuleId = null, status = RuleStatus.ACTIVE.name)
                )
                "已改为一次性" + (splice?.let { " · $it" } ?: "")
            } else {
                db.ruleDao().update(
                    r.copy(
                        kind = RuleKind.REPEATING.name,
                        dueDate = null,
                        status = RuleStatus.ACTIVE.name
                    )
                )
                "已改为长期预案，在根目录清单里"
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(msg) }
        }

    private suspend fun chainCompletedHead(ruleId: Long): Long? {
        val active = db.ruleDao().observeAll().first().filter { it.status == RuleStatus.ACTIVE.name }
        val path = Chain.chains(active).firstOrNull { p -> p.any { it.id == ruleId } } ?: return null
        val done = db.execLogDao().observeSince(dayStart(System.currentTimeMillis())).first()
            .filter { it.done == true }.mapTo(HashSet()) { it.ruleId }
        return path.first().id.takeIf { path.all { it.id in done } }
    }

    /** 晚上补记：先记一条「待定」，之后再标做了/没做 */
    fun logPending(ruleId: Long) = viewModelScope.launch(Dispatchers.IO) {
        db.execLogDao().insert(ExecLog(ruleId = ruleId, done = null, backfill = true))
    }

    fun resolveLog(log: ExecLog, done: Boolean?) = viewModelScope.launch(Dispatchers.IO) {
        db.execLogDao().update(log.copy(done = done))
    }

    fun deleteLog(log: ExecLog) = viewModelScope.launch(Dispatchers.IO) {
        db.execLogDao().delete(log)
    }

    // ---- 预案动作 ----

    fun saveRule(rule: Rule, onSaved: (Long) -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        val toSave = if (rule.id == 0L) {
            rule.copy(sortOrder = db.ruleDao().maxSortOrder(rule.folderId) + 1)
        } else rule
        val id = db.ruleDao().upsert(toSave)
        kotlinx.coroutines.withContext(Dispatchers.Main) { onSaved(if (rule.id != 0L) rule.id else id) }
    }

    suspend fun getRule(id: Long): Rule? = db.ruleDao().getById(id)

    fun setArchived(rule: Rule, archived: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (archived) spliceOut(rule.id)
        db.ruleDao().update(
            rule.copy(status = if (archived) RuleStatus.ARCHIVED.name else RuleStatus.ACTIVE.name)
        )
    }

    /** 删除：链条中间的节点被抽走时，前驱直接接上它的后继 */
    fun deleteRule(rule: Rule, onResult: (String) -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        val pred = db.ruleDao().predecessorOf(rule.id)
        val tail = rule.nextRuleId?.let { db.ruleDao().getById(it) }
        if (pred != null) db.ruleDao().updateNext(pred.id, if (tail != null && tail.status == RuleStatus.ACTIVE.name) tail.id else null)
        db.execLogDao().deleteForRules(listOf(rule.id))
        db.ruleDao().delete(rule)
        val msg = if (pred != null && tail != null && tail.id != pred.id) {
            "已删除 · 链条已接上「${pred.ifText} → ${tail.ifText}」"
        } else "已删除"
        kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(msg) }
    }

    /** 把节点从链条里抽走，前后自动接上；返回给用户看的一句说明 */
    private suspend fun spliceOut(id: Long): String? {
        val self = db.ruleDao().getById(id) ?: return null
        val pred = db.ruleDao().predecessorOf(id) ?: return null
        val tail = self.nextRuleId?.let { db.ruleDao().getById(it) }
        db.ruleDao().updateNext(pred.id, tail?.id)
        return tail?.let { "已从链条摘出「${pred.ifText} → ${it.ifText}」" }
            ?: "已从链条摘出「${pred.ifText}」之后"
    }

    // ---- 链条连接 ----

    /** 候选：非自己、无前驱、接上不会成环；一次性预案不参与链条 */
    suspend fun linkCandidates(selfId: Long): List<Rule> {
        val active = db.ruleDao().observeAll().first()
            .filter { it.status == RuleStatus.ACTIVE.name && !it.isOneOff }
        return Chain.linkCandidates(active, selfId)
    }

    fun linkNext(fromId: Long, toId: Long?, onResult: (String?) -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        val active = db.ruleDao().observeAll().first()
            .filter { it.status == RuleStatus.ACTIVE.name && !it.isOneOff }
        val next = Chain.sanitizedNext(active)
        if (toId != null) {
            if (Chain.wouldCycle(fromId, toId, next)) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onResult("接上去会形成循环，已拒绝") }
                return@launch
            }
            // 每个节点最多一个前驱：目标已有前驱就把那条边挪走
            active.firstOrNull { next[it.id] == toId && it.id != fromId }
                ?.let { db.ruleDao().updateNext(it.id, null) }
        }
        db.ruleDao().updateNext(fromId, toId)
        val size = if (toId == null) 0 else {
            val reloaded = db.ruleDao().observeAll().first()
                .filter { it.status == RuleStatus.ACTIVE.name && !it.isOneOff }
            Chain.chains(reloaded).firstOrNull { p -> p.any { it.id == fromId } }?.size ?: 0
        }
        val msg = when {
            toId == null -> "已取消连接"
            size > Constants.CHAIN_MAX_STEPS -> "已接上 · 这条链已有 $size 步，建议拆成两条"
            else -> null
        }
        kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(msg) }
    }

    /** 在该节点处断开（切断进入它的那条边）；它自己的后继成为新头部 */
    fun unlinkAt(ruleId: Long, onResult: (String?) -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        val pred = db.ruleDao().predecessorOf(ruleId)
        if (pred == null) {
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult("这条就是链条开头，没有可断开的连接") }
            return@launch
        }
        db.ruleDao().updateNext(pred.id, null)
        kotlinx.coroutines.withContext(Dispatchers.Main) { onResult("已在这里断开") }
    }

    // ---- 排序 ----

    fun moveRuleStep(ruleId: Long, delta: Int) = reorderRule(ruleId, delta = delta)
    fun moveRuleToEdge(ruleId: Long, top: Boolean) = reorderRule(ruleId, top = top)

    private fun reorderRule(ruleId: Long, delta: Int = 0, top: Boolean? = null) =
        viewModelScope.launch(Dispatchers.IO) {
            val rule = db.ruleDao().getById(ruleId) ?: return@launch
            // 只在同一层「看得见的」条目之间移动：已归档和已结清的不参与换位
            val sibs = ArrayList(
                db.ruleDao().observeAll().first().filter {
                    it.folderId == rule.folderId && it.kind == rule.kind &&
                        it.status == RuleStatus.ACTIVE.name
                }
            )
            val from = sibs.indexOfFirst { it.id == ruleId }
            if (from < 0) return@launch
            val to = when {
                top == true -> 0
                top == false -> sibs.lastIndex
                else -> (from + delta).coerceIn(0, sibs.lastIndex)
            }
            if (to == from) return@launch
            sibs.add(to, sibs.removeAt(from))
            sibs.forEachIndexed { i, s -> if (s.sortOrder != i) db.ruleDao().updateSortOrder(s.id, i) }
        }

    fun moveFolderStep(folderId: Long, delta: Int) = reorderFolder(folderId, delta = delta)
    fun moveFolderToEdge(folderId: Long, top: Boolean) = reorderFolder(folderId, top = top)

    private fun reorderFolder(folderId: Long, delta: Int = 0, top: Boolean? = null) =
        viewModelScope.launch(Dispatchers.IO) {
            val all = db.folderDao().observeAll().first()
            val folder = all.firstOrNull { it.id == folderId } ?: return@launch
            val sibs = ArrayList(all.filter { it.parentId == folder.parentId })
            val from = sibs.indexOfFirst { it.id == folderId }
            if (from < 0) return@launch
            val to = when {
                top == true -> 0
                top == false -> sibs.lastIndex
                else -> (from + delta).coerceIn(0, sibs.lastIndex)
            }
            if (to == from) return@launch
            sibs.add(to, sibs.removeAt(from))
            sibs.forEachIndexed { i, f -> if (f.sortOrder != i) db.folderDao().update(f.copy(sortOrder = i)) }
        }

    fun moveRule(ruleId: Long, folderId: Long?) = viewModelScope.launch(Dispatchers.IO) {
        db.ruleDao().getById(ruleId)?.let {
            db.ruleDao().update(
                it.copy(
                    folderId = folderId,
                    sortOrder = db.ruleDao().maxSortOrder(folderId) + 1
                )
            )
        }
    }

    fun addCandidates(candidates: List<AiCandidate>, folderId: Long?, kind: String) =
        viewModelScope.launch(Dispatchers.IO) {
            var order = db.ruleDao().maxSortOrder(folderId) + 1
            candidates.forEach { c ->
                db.ruleDao().upsert(
                    Rule(
                        ifText = c.ifText, thenText = c.thenText,
                        folderId = folderId, sortOrder = order++, kind = kind
                    )
                )
            }
        }

    // ---- 文件夹动作 ----

    fun openFolder(id: Long?) {
        currentFolderId.value = id
    }

    fun createFolder(name: String, emoji: String, color: Long) = viewModelScope.launch(Dispatchers.IO) {
        val parent = currentFolderId.value
        db.folderDao().upsert(
            Folder(
                name = name.trim(), emoji = emoji, color = color, parentId = parent,
                sortOrder = db.folderDao().maxSortOrder(parent) + 1
            )
        )
    }

    fun updateFolder(folder: Folder) = viewModelScope.launch(Dispatchers.IO) {
        db.folderDao().update(folder)
    }

    /** 级联统计：夹子树里的预案数与记录数，供确认弹窗展示 */
    suspend fun folderDeleteImpact(folderId: Long): Pair<Int, Int> {
        val ids = subtreeIds(folderId)
        val rules = db.ruleDao().rulesInFolders(ids)
        val allLogs = db.execLogDao().observeSince(0L).first()
        val ruleIds = rules.map { it.id }.toSet()
        return rules.size to allLogs.count { it.ruleId in ruleIds }
    }

    fun deleteFolderCascade(folderId: Long) = viewModelScope.launch(Dispatchers.IO) {
        val ids = subtreeIds(folderId)
        val rules = db.ruleDao().rulesInFolders(ids)
        val doomed = rules.map { it.id }.toSet()
        db.execLogDao().deleteForRules(rules.map { it.id })
        db.ruleDao().deleteByIds(rules.map { it.id })
        ids.forEach { db.folderDao().deleteById(it) }
        // 存活预案里指向已删节点的链条指针要清掉
        db.ruleDao().observeAll().first().forEach { r ->
            r.nextRuleId?.let { if (it in doomed) db.ruleDao().updateNext(r.id, null) }
        }
        if (currentFolderId.value?.let { it in ids } == true) {
            currentFolderId.value = null
        }
    }

    private suspend fun subtreeIds(folderId: Long): List<Long> {
        val folders = db.folderDao().observeAll().first()
        val childrenOf = folders.groupBy { it.parentId }
        val out = ArrayList<Long>()
        fun walk(id: Long) {
            out.add(id)
            childrenOf[id].orEmpty().forEach { walk(it.id) }
        }
        walk(folderId)
        return out
    }

    // ---- AI / 设置 / 备份 ----

    fun generateCandidates(concern: String, onResult: (Result<List<AiCandidate>>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = apiSettings.value
            val r = runCatching { aiClient.generateCandidates(settings, concern) }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(r) }
        }
    }

    fun testApi(s: ApiSettings, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching { aiClient.testConnection(s) }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(r) }
        }
    }

    fun saveApiSettings(s: ApiSettings) = viewModelScope.launch(Dispatchers.IO) {
        settingsRepo.saveApiSettings(s)
    }

    fun exportData(uri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching { backupRepo.exportTo(uri) }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(r) }
        }
    }

    fun importData(uri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching { backupRepo.importFrom(uri) }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(r) }
        }
    }
}
