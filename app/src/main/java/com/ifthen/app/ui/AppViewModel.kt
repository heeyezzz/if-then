package com.ifthen.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ifthen.app.Constants
import com.ifthen.app.ReviewWindow
import com.ifthen.app.ai.AiCandidate
import com.ifthen.app.ai.DeepSeekClient
import com.ifthen.app.ai.ReviewInsight
import com.ifthen.app.update.UpdateChecker
import com.ifthen.app.update.UpdateInfo
import com.ifthen.app.data.ApiSettings
import com.ifthen.app.data.AppDatabase
import com.ifthen.app.data.BackupRepository
import com.ifthen.app.data.Chain
import com.ifthen.app.data.ChainGraph
import com.ifthen.app.data.ChainStatus
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class RuleStat(
    val streak: Int = 0,
    /** 以下计数都按复盘页当前时间范围算，不是固定 30 天 */
    val triggers: Int = 0,
    val done: Int = 0,
    val lastTriggerAt: Long? = null
) {
    /** 样本不够 [minSample] 次时不给百分比，避免「触发 5 次的 100%」冒充结论 */
    fun rate(window: ReviewWindow): Int? =
        if (triggers < window.minSample) null else done * 100 / triggers
}

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
    val triggers: Int,
    val done: Int
) {
    fun rate(window: ReviewWindow): Int? = if (triggers < window.minSample) null else done * 100 / triggers
}

/** 热力图一格：按天或按周聚合，带当天流水供点击就地展开 */
data class HeatCell(
    val label: String,
    val count: Int,
    val isToday: Boolean,
    val logs: List<ExecLog>
)

data class Heat(val cells: List<HeatCell>, val byWeek: Boolean)

/** 结论卡之一：窗口内执行率最低、且样本够得上一条长期预案 */
data class WorstRule(val rule: Rule, val triggers: Int, val done: Int, val rate: Int)

data class OneOffStats(
    val doneRules: List<Rule> = emptyList(),
    val missedRules: List<Rule> = emptyList(),
    val pendingRules: List<Rule> = emptyList(),
    val overdueRules: List<Rule> = emptyList()
) {
    val done: Int get() = doneRules.size
    val missed: Int get() = missedRules.size
    val pending: Int get() = pendingRules.size
    val overdue: Int get() = overdueRules.size
    val rate: Int? get() = if (done + missed == 0) null else done * 100 / (done + missed)
}

data class AppUiState(
    val activeRules: List<Rule> = emptyList(),
    val archivedRules: List<Rule> = emptyList(),
    val oneOffActive: List<Rule> = emptyList(),
    val oneOffResolvedToday: List<Rule> = emptyList(),
    val oneOffOverdueDays: Map<Long, Int> = emptyMap(),
    val miniDone: Int = 0,
    val totalDone: Int = 0,
    val oneOffStats: OneOffStats = OneOffStats(),
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
    val reviewWindow: ReviewWindow = ReviewWindow.MONTH,
    val heat: Heat = Heat(emptyList(), false),
    val zombies: List<Rule> = emptyList(),
    /** 结论卡：最该改的那条 + 链条最常断的那一步 */
    val worst: WorstRule? = null,
    val breakPoint: ChainHealth? = null,
    val ruleById: Map<Long, Rule> = emptyMap(),
    val chains: List<Chain> = emptyList(),
    val chainById: Map<Long, Chain> = emptyMap(),
    val chain: ChainSnapshot = ChainSnapshot()
)

/** 复盘页 AI 点评：只在用户点按时请求，结果按窗口 + 数据指纹缓存在内存 */
sealed interface AiReview {
    object Idle : AiReview
    object Loading : AiReview
    data class Failed(val message: String) : AiReview
    data class Ready(val insight: ReviewInsight, val ruleIds: List<Long>, val fingerprint: Long) : AiReview
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val settingsRepo = SettingsRepository(app)
    private val backupRepo = BackupRepository(app)
    private val aiClient = DeepSeekClient()
    private val updateChecker = UpdateChecker()

    val apiSettings: StateFlow<ApiSettings> = settingsRepo.apiSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiSettings("", "", ""))

    private val now = MutableStateFlow(System.currentTimeMillis())
    private val currentFolderId = MutableStateFlow<Long?>(null)
    private val reviewWindow = MutableStateFlow(ReviewWindow.MONTH)

    fun setReviewWindow(w: ReviewWindow) {
        reviewWindow.value = w
    }

    val ui: StateFlow<AppUiState> = combine(
        db.ruleDao().observeAll(),
        db.execLogDao().observeSince(0L),
        db.folderDao().observeAll(),
        db.chainDao().observeAll(),
        combine(now, currentFolderId, reviewWindow) { t, f, w -> Triple(t, f, w) }
    ) { rules, logs, folders, chains, extra ->
        buildState(rules, logs, folders, chains, extra.first, extra.second, extra.third)
    }
        // buildState 要重算全量分组/连击/热力图/链条，放在主线程会在每次数据变化时卡住 UI
        .flowOn(Dispatchers.Default)
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
        chains: List<Chain>,
        ts: Long,
        folderId: Long?,
        window: ReviewWindow
    ): AppUiState {
        val activeAll = rules.filter { it.status == RuleStatus.ACTIVE.name }
        // 长期清单才是"战绩/链条/文件夹"的主场；一次性另成一页
        val active = activeAll.filterNot { it.isOneOff }
        val archived = rules.filter { it.status == RuleStatus.ARCHIVED.name }
        val byRule = logs.groupBy { it.ruleId }
        val dayMs = 24L * 3600_000
        val todayStart = dayStart(ts)
        // 窗口按自然日对齐，跟热力图的格子同一套切法；「全部」就是没有起点
        val since = if (window.days == 0) 0L else todayStart - (window.days - 1) * dayMs
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
            val inWindow = rl.filter { it.createdAt >= since }
            r.id to RuleStat(
                streak = streak,
                triggers = inWindow.size,
                done = inWindow.count { it.done == true },
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

        // 7 天窗口判「僵尸」不公平，这一档整节不出现
        val zombies = if (window.days in 1 until Constants.ZOMBIE_DAYS) emptyList() else active.filter { r ->
            r.createdAt < since && (stats[r.id]?.lastTriggerAt ?: 0L) < since
        }

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
        val resolvedInWindow = resolvedOneOff.filter { r ->
            (byRule[r.id]?.firstOrNull()?.createdAt ?: 0L) >= since
        }
        val oneOffStats = OneOffStats(
            doneRules = resolvedInWindow.filter { it.status == RuleStatus.DONE.name },
            missedRules = resolvedInWindow.filter { it.status == RuleStatus.MISSED.name },
            pendingRules = oneOffAll,
            overdueRules = late
        )
        val doneLogs = logs.filter { it.done == true && it.createdAt >= since }
        val heat = buildHeat(logs, todayStart, dayMs, window)

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
                        triggers = memberRules.sumOf { stats[it.id]?.triggers ?: 0 },
                        done = memberRules.sumOf { stats[it.id]?.done ?: 0 }
                    )
                )
                walk(f.id, depth + 1)
            }
        }
        walk(null, 0)

        // 「全部」档下链条不能无限回溯，封顶 90 天并在标题上写实际天数
        val allSpan = if (logs.isEmpty()) Constants.CHAIN_HEALTH_DAYS
        else ((todayStart - logs.minOf { it.createdAt }) / dayMs).toInt() + 1
        val healthDays = if (window.days == 0) {
            allSpan.coerceIn(1, Constants.CHAIN_HEALTH_ALL_MAX_DAYS)
        } else window.days

        val chain = ChainState.compute(
            allActive = active,
            inFolder = visibleRules,
            logs = logs,
            todayStart = todayStart,
            healthDays = healthDays
        )

        val worst = active.mapNotNull { r ->
            val s = stats[r.id] ?: return@mapNotNull null
            val rate = s.rate(window) ?: return@mapNotNull null
            WorstRule(r, s.triggers, s.done, rate)
        }.minWithOrNull(compareBy<WorstRule> { it.rate }.thenByDescending { it.triggers })

        val breakPoint = chain.health.filter { it.worstStep > 0 }
            .maxByOrNull { it.breakAt.getOrElse(it.worstStep - 1) { 0 } }

        return AppUiState(
            activeRules = active,
            archivedRules = archived,
            oneOffActive = onTime + late,
            oneOffResolvedToday = oneOffResolvedToday,
            oneOffOverdueDays = late.associate { it.id to ((todayStart - it.dueDate!!) / dayMs).toInt() + 1 },
            miniDone = doneLogs.count { it.mini },
            totalDone = doneLogs.size,
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
            reviewWindow = window,
            heat = heat,
            zombies = zombies,
            worst = worst,
            breakPoint = breakPoint,
            ruleById = rules.associateBy { it.id },
            chains = chains,
            chainById = chains.associateBy { it.id },
            chain = chain
        )
    }

    /** 热力图：7/30 天一天一格，「全部」按周一格且只看最近 52 周 */
    private fun buildHeat(
        logs: List<ExecLog>,
        todayStart: Long,
        dayMs: Long,
        window: ReviewWindow
    ): Heat {
        val fmt = SimpleDateFormat("M/d", Locale.CHINA)
        val cells = ArrayList<HeatCell>()
        if (window == ReviewWindow.ALL) {
            val weekMs = 7 * dayMs
            val thisWeek = weekStart(todayStart, dayMs)
            val earliest = logs.minOfOrNull { it.createdAt } ?: todayStart
            val weeks = (((thisWeek - earliest) / weekMs).toInt() + 1)
                .coerceIn(1, Constants.HEATMAP_MAX_WEEKS)
            for (i in weeks - 1 downTo 0) {
                val w0 = thisWeek - i * weekMs
                val w1 = w0 + weekMs
                val inWeek = logs.filter { it.createdAt in w0 until w1 }
                cells.add(
                    HeatCell(
                        label = "${fmt.format(Date(w0))} 那周",
                        count = inWeek.count { it.done == true },
                        isToday = i == 0,
                        logs = inWeek
                    )
                )
            }
            return Heat(cells, byWeek = true)
        }
        for (i in window.days - 1 downTo 0) {
            val d0 = todayStart - i * dayMs
            val dayLogs = logs.filter { it.createdAt in d0 until d0 + dayMs }
            cells.add(
                HeatCell(
                    label = fmt.format(Date(d0)),
                    count = dayLogs.count { it.done == true },
                    isToday = i == 0,
                    logs = dayLogs
                )
            )
        }
        return Heat(cells, byWeek = false)
    }

    private fun weekStart(dayStartTs: Long, dayMs: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = dayStartTs }
        val dow = c.get(Calendar.DAY_OF_WEEK)
        val sinceMonday = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        return dayStartTs - sinceMonday * dayMs
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
                    r.copy(kind = RuleKind.ONE_OFF.name, chainId = null, stepIndex = null, status = RuleStatus.ACTIVE.name)
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

    /** 编辑页保存：只有类型变了才按原语义转换（转一次性会摘出链条并清链位、转长期清到期），其余字段照常入库 */
    fun saveRuleEdited(original: Rule?, updated: Rule, onSaved: (Long) -> Unit = {}) =
        viewModelScope.launch(Dispatchers.IO) {
            var toSave = updated
            val kindChanged = original != null && original.kind != updated.kind
            if (kindChanged) {
                if (updated.kind == RuleKind.ONE_OFF.name) {
                    spliceOut(original!!.id)
                    toSave = toSave.copy(chainId = null, stepIndex = null, status = RuleStatus.ACTIVE.name)
                } else {
                    toSave = toSave.copy(dueDate = null, status = RuleStatus.ACTIVE.name)
                }
            }
            val finalRule = if (toSave.id == 0L)
                toSave.copy(sortOrder = db.ruleDao().maxSortOrder(toSave.folderId) + 1) else toSave
            val id = db.ruleDao().upsert(finalRule)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onSaved(if (toSave.id != 0L) toSave.id else id)
            }
        }

    private suspend fun chainCompletedHead(ruleId: Long): Long? {
        val active = db.ruleDao().observeAll().first().filter { it.status == RuleStatus.ACTIVE.name }
        val path = ChainGraph.paths(active).firstOrNull { p -> p.any { it.id == ruleId } } ?: return null
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

    /** 某预案在其链里的后继 id；不在链里或已是末步则 null */
    suspend fun successorOf(ruleId: Long): Long? = withContext(Dispatchers.IO) {
        val r = db.ruleDao().getById(ruleId) ?: return@withContext null
        val cid = r.chainId ?: return@withContext null
        val m = db.ruleDao().chainMembers(cid)
        val i = m.indexOfFirst { it.id == ruleId }
        if (i >= 0 && i < m.lastIndex) m[i + 1].id else null
    }

    fun setArchived(rule: Rule, archived: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (archived) spliceOut(rule.id)
        db.ruleDao().update(
            rule.copy(status = if (archived) RuleStatus.ARCHIVED.name else RuleStatus.ACTIVE.name)
        )
    }

    /** 删除：成员被抽走时先愈合它所在的链，再删预案 */
    fun deleteRule(rule: Rule, onResult: (String) -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        val note = spliceOut(rule.id)
        db.execLogDao().deleteForRules(listOf(rule.id))
        db.ruleDao().delete(rule)
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            onResult(note?.let { "已删除 · $it" } ?: "已删除")
        }
    }

    /** 把节点从它所在的链里摘走，其余按序愈合；链掉到不足 2 步则整条散开 */
    private suspend fun spliceOut(id: Long): String? {
        val self = db.ruleDao().getById(id) ?: return null
        val cid = self.chainId ?: return null
        val members = db.ruleDao().chainMembers(cid)
        db.ruleDao().setMembership(id, null, null)
        val rest = members.filter { it.id != id }
        if (rest.size < 2) {
            rest.forEach { db.ruleDao().setMembership(it.id, null, null) }
            db.chainDao().deleteById(cid)
            return "所在链条只剩一步，已散开"
        }
        rest.forEachIndexed { i, r -> db.ruleDao().setMembership(r.id, cid, i) }
        return "已从链条摘出「${self.ifText}」，前后接上"
    }

    // ---- 链条连接 ----

    /** 候选：活跃长期预案，排除自己和已在同一链里的成员（实体模型不成环，无需 wouldCycle） */
    suspend fun linkCandidates(selfId: Long): List<Rule> {
        val active = db.ruleDao().observeAll().first()
            .filter { it.status == RuleStatus.ACTIVE.name && !it.isOneOff }
        val myChain = db.ruleDao().getById(selfId)?.chainId
        val inMyChain = if (myChain != null) db.ruleDao().chainMembers(myChain).map { it.id }.toSet()
        else emptySet()
        return active.filter { it.id != selfId && it.id !in inMyChain }
    }

    fun linkNext(fromId: Long, toId: Long?, onResult: (String?) -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        val msg = if (toId == null) unlinkAfter(fromId) else attachNext(fromId, toId)
        kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(msg) }
    }

    /** 让 toId 紧跟 fromId：toId 若在原链先摘出愈合，再插到 fromId 之后 */
    private suspend fun attachNext(fromId: Long, toId: Long): String? {
        val from = db.ruleDao().getById(fromId) ?: return null
        val to = db.ruleDao().getById(toId) ?: return null
        if (from.chainId != null && from.chainId == to.chainId) {
            val cid = from.chainId!!
            val ids = db.ruleDao().chainMembers(cid).map { it.id }.toMutableList()
            ids.remove(toId)
            val at = (ids.indexOf(fromId) + 1).coerceAtLeast(0)
            ids.add(at, toId)
            ids.forEachIndexed { i, rid -> db.ruleDao().setMembership(rid, cid, i) }
            return sizeNote(cid)
        }
        if (to.chainId != null) spliceOut(toId)
        val cid: Long = from.chainId ?: db.chainDao().upsert(
            Chain(name = from.ifText, folderId = from.folderId, createdAt = System.currentTimeMillis())
        ).also { db.ruleDao().setMembership(fromId, it, 0) }
        val members = db.ruleDao().chainMembers(cid).toMutableList()
        val at = (members.indexOfFirst { it.id == fromId } + 1).coerceAtLeast(0)
        members.add(at, to)
        members.forEachIndexed { i, r -> db.ruleDao().setMembership(r.id, cid, i) }
        db.chainDao().getById(cid)?.let { ch -> if (ch.folderId != from.folderId) db.chainDao().update(ch.copy(folderId = from.folderId)) }
        return sizeNote(cid)
    }

    /** 把 fromId 的后继断开：其后成员另起新链，不足 2 步退回散预案 */
    private suspend fun unlinkAfter(fromId: Long): String? {
        val from = db.ruleDao().getById(fromId) ?: return null
        val cid = from.chainId ?: return "已取消连接"
        val members = db.ruleDao().chainMembers(cid)
        val idx = members.indexOfFirst { it.id == fromId }
        if (idx < 0 || idx == members.lastIndex) return "已取消连接"
        val head = members.subList(0, idx + 1)
        val tail = members.subList(idx + 1, members.size)
        if (head.size < 2) { head.forEach { db.ruleDao().setMembership(it.id, null, null) }; db.chainDao().deleteById(cid) }
        else head.forEachIndexed { i, r -> db.ruleDao().setMembership(r.id, cid, i) }
        if (tail.size >= 2) {
            val ncid = db.chainDao().upsert(
                Chain(name = tail.first().ifText, folderId = tail.first().folderId, createdAt = System.currentTimeMillis())
            )
            tail.forEachIndexed { i, r -> db.ruleDao().setMembership(r.id, ncid, i) }
        } else tail.forEach { db.ruleDao().setMembership(it.id, null, null) }
        return "已取消连接"
    }

    /** 在该节点处断开：它及其后继另起新链，前段留原链 */
    fun unlinkAt(ruleId: Long, onResult: (String?) -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        val r = db.ruleDao().getById(ruleId)
        val cid = r?.chainId
        if (cid == null) {
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult("这条不在任何链条里") }
            return@launch
        }
        val members = db.ruleDao().chainMembers(cid)
        val idx = members.indexOfFirst { it.id == ruleId }
        if (idx <= 0) {
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult("这条就是链条开头，没有可断开的连接") }
            return@launch
        }
        val head = members.subList(0, idx)
        val tail = members.subList(idx, members.size)
        if (head.size < 2) { head.forEach { db.ruleDao().setMembership(it.id, null, null) }; db.chainDao().deleteById(cid) }
        else head.forEachIndexed { i, x -> db.ruleDao().setMembership(x.id, cid, i) }
        if (tail.size >= 2) {
            val ncid = db.chainDao().upsert(
                Chain(name = r.ifText, folderId = r.folderId, createdAt = System.currentTimeMillis())
            )
            tail.forEachIndexed { i, x -> db.ruleDao().setMembership(x.id, ncid, i) }
        } else db.ruleDao().setMembership(ruleId, null, null)
        kotlinx.coroutines.withContext(Dispatchers.Main) { onResult("已在这里断开") }
    }

    private suspend fun sizeNote(cid: Long): String? {
        val size = db.ruleDao().chainMembers(cid).size
        return if (size > Constants.CHAIN_MAX_STEPS) "已接上 · 这条链已有 $size 步，建议拆成两条" else null
    }

    // ---- 链级操作（实体化后） ----

    val expandedChains: StateFlow<Set<Long>> = settingsRepo.expandedChains
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleChainExpanded(chainId: Long, expanded: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        settingsRepo.setChainExpanded(chainId, expanded)
    }

    fun renameChain(chainId: Long, name: String) = viewModelScope.launch(Dispatchers.IO) {
        db.chainDao().getById(chainId)?.let {
            val n = name.trim()
            if (n.isNotEmpty()) db.chainDao().update(it.copy(name = n))
        }
    }

    fun setChainColor(chainId: Long, color: Int?) = viewModelScope.launch(Dispatchers.IO) {
        db.chainDao().getById(chainId)?.let { db.chainDao().update(it.copy(color = color)) }
    }

    /** 整条链挪到别的文件夹：改链的夹，成员预案的夹跟着走 */
    fun setChainFolder(chainId: Long, folderId: Long?) = viewModelScope.launch(Dispatchers.IO) {
        db.chainDao().getById(chainId)?.let { db.chainDao().update(it.copy(folderId = folderId)) }
        db.ruleDao().chainMembers(chainId).forEach { r ->
            if (r.folderId != folderId) {
                db.ruleDao().update(r.copy(folderId = folderId, sortOrder = db.ruleDao().maxSortOrder(folderId) + 1))
            }
        }
    }

    /** 归档整条链：链与成员一并置归档，退出活动首页与复盘统计，不删 */
    fun archiveChain(chainId: Long) = setChainArchived(chainId, true)
    fun unarchiveChain(chainId: Long) = setChainArchived(chainId, false)

    private fun setChainArchived(chainId: Long, archived: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val st = if (archived) ChainStatus.ARCHIVED.name else ChainStatus.ACTIVE.name
        db.chainDao().getById(chainId)?.let { db.chainDao().update(it.copy(status = st)) }
        val rs = if (archived) RuleStatus.ARCHIVED.name else RuleStatus.ACTIVE.name
        db.ruleDao().chainMembers(chainId).forEach { db.ruleDao().update(it.copy(status = rs)) }
    }

    /** 删整串：deleteRules=false 只拆链留预案，=true 连成员预案带打卡一起删 */
    fun deleteChain(chainId: Long, deleteRules: Boolean, onResult: (String) -> Unit = {}) =
        viewModelScope.launch(Dispatchers.IO) {
            val members = db.ruleDao().chainMembers(chainId)
            if (deleteRules) {
                val ids = members.map { it.id }
                db.execLogDao().deleteForRules(ids)
                db.ruleDao().deleteByIds(ids)
            } else {
                members.forEach { db.ruleDao().setMembership(it.id, null, null) }
            }
            db.chainDao().deleteById(chainId)
            withContext(Dispatchers.Main) {
                onResult(
                    if (deleteRules) "已删除整条链（含 ${members.size} 步预案与打卡）"
                    else "已拆链，${members.size} 步预案留在文件夹"
                )
            }
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

    /** 链条整串保存：一行一步，id=0 是还没入库的新步骤；broken 表示这一步与前一步之间断开 */
    data class ChainStep(val id: Long, val ifText: String, val thenText: String, val broken: Boolean)

    /** 读一条链作为整串编辑的初值（headId 为链头预案 id） */
    suspend fun chainSteps(headId: Long): List<ChainStep> = withContext(Dispatchers.IO) {
        val r = db.ruleDao().getById(headId) ?: return@withContext emptyList()
        val cid = r.chainId ?: return@withContext emptyList()
        db.ruleDao().chainMembers(cid).map { ChainStep(it.id, it.ifText, it.thenText, false) }
    }

    /** 可作为「换成已有的」的候选：活跃长期预案，排除已在本串里的；带上它现有的链位置供界面标注 */
    suspend fun chainPickable(inUse: Set<Long>): List<Pair<Rule, ChainPos?>> =
        withContext(Dispatchers.IO) {
            val active = db.ruleDao().observeAll().first()
                .filter { it.status == RuleStatus.ACTIVE.name && !it.isOneOff }
            val pos = HashMap<Long, ChainPos>()
            ChainGraph.paths(active).forEach { path ->
                path.forEachIndexed { i, r -> pos[r.id] = ChainPos(path.first().id, i + 1, path.size) }
            }
            active.filter { it.id !in inUse }
                .map { it to pos[it.id] }
                .sortedWith(
                    compareBy({ it.second?.headId ?: -1L }, { it.second?.step ?: 0 })
                )
        }

    fun saveChain(steps: List<ChainStep>, name: String, folderId: Long?, onResult: (String) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            // 1) 行按 broken 拆段，跳过空的新行
            val segments = ArrayList<MutableList<ChainStep>>()
            var cur = ArrayList<ChainStep>()
            steps.forEachIndexed { i, s ->
                val ifT = s.ifText.trim(); val thT = s.thenText.trim()
                if (s.id == 0L && (ifT.isBlank() || thT.isBlank())) return@forEachIndexed
                val row = s.copy(ifText = ifT, thenText = thT)
                if (i > 0 && s.broken && cur.isNotEmpty()) { segments.add(cur); cur = ArrayList(); cur.add(row) }
                else cur.add(row)
            }
            if (cur.isNotEmpty()) segments.add(cur)
            if (segments.isEmpty()) {
                withContext(Dispatchers.Main) { onResult("没有可保存的步骤") }; return@launch
            }

            // 2) 每行落成入库的 ruleId（新行建散预案，旧行改文字+夹）
            val segIds = segments.map { seg ->
                seg.mapNotNull { s ->
                    if (s.id > 0L) {
                        val r = db.ruleDao().getById(s.id) ?: return@mapNotNull null
                        if (r.ifText != s.ifText || r.thenText != s.thenText || r.folderId != folderId)
                            db.ruleDao().update(r.copy(ifText = s.ifText, thenText = s.thenText, folderId = folderId))
                        r.id
                    } else {
                        db.ruleDao().upsert(
                            Rule(
                                ifText = s.ifText, thenText = s.thenText, folderId = folderId,
                                sortOrder = db.ruleDao().maxSortOrder(folderId) + 1,
                                kind = RuleKind.REPEATING.name
                            )
                        )
                    }
                }
            }

            // 3) 被编辑的链 = 第一个已存在行所在的链；其中不再出现的步退回散预案
            val editedChainId = steps.firstOrNull { it.id > 0L }?.let { db.ruleDao().getById(it.id)?.chainId }
            if (editedChainId != null) {
                val keep = segIds.flatten().toSet()
                db.ruleDao().chainMembers(editedChainId)
                    .forEach { if (it.id !in keep) db.ruleDao().setMembership(it.id, null, null) }
            }

            // 4) 逐段挂链：首段复用被编辑链(或新建)并用名字框命名，其余各建新链；段不足 2 步就散
            val trimmed = name.trim()
            var chainCount = 0
            segIds.forEachIndexed { si, ids ->
                if (ids.isEmpty()) return@forEachIndexed
                if (ids.size < 2) {
                    ids.forEach { db.ruleDao().setMembership(it, null, null) }
                    return@forEachIndexed
                }
                val headIf = db.ruleDao().getById(ids.first())?.ifText ?: "未命名链"
                val autoName = if (si == 0 && trimmed.isNotEmpty()) trimmed else headIf
                val cid = if (si == 0 && editedChainId != null) {
                    db.chainDao().getById(editedChainId)?.let {
                        db.chainDao().update(it.copy(name = autoName, folderId = folderId))
                    }
                    editedChainId
                } else {
                    db.chainDao().upsert(
                        Chain(name = autoName, folderId = folderId, createdAt = System.currentTimeMillis())
                    )
                }
                ids.forEachIndexed { i, rid -> db.ruleDao().setMembership(rid, cid, i) }
                chainCount += 1
            }

            // 5) 统一步散与重排：不足 2 步的残链解散，够长的按 0..n-1 补齐连续序号（覆盖换已有留下的空洞）
            db.ruleDao().referencedChainIds().forEach { cid ->
                val m = db.ruleDao().chainMembers(cid)
                if (m.size < 2) {
                    m.forEach { db.ruleDao().setMembership(it.id, null, null) }
                    db.chainDao().deleteById(cid)
                } else {
                    m.forEachIndexed { i, r -> if (r.stepIndex != i) db.ruleDao().setMembership(r.id, cid, i) }
                }
            }

            val total = segIds.sumOf { it.size }
            withContext(Dispatchers.Main) {
                onResult(if (chainCount > 1) "已保存 $total 步，拆成 $chainCount 条链" else "已保存 $total 步链")
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
        db.execLogDao().deleteForRules(rules.map { it.id })
        db.ruleDao().deleteByIds(rules.map { it.id })
        ids.forEach { db.folderDao().deleteById(it) }
        // 成员被删后掉到不足 2 步、或所在夹已被删的链，一并解散：剩余成员退回散预案
        db.chainDao().observeAll().first().forEach { ch ->
            val m = db.ruleDao().chainMembers(ch.id)
            if (m.size < 2 || (ch.folderId != null && ch.folderId in ids)) {
                m.forEach { db.ruleDao().setMembership(it.id, null, null) }
                db.chainDao().deleteById(ch.id)
            }
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

    private val _aiReview = MutableStateFlow<AiReview>(AiReview.Idle)
    val aiReview: StateFlow<AiReview> = _aiReview

    /** 复盘点评只在用户点按时发请求；同一窗口同一份数据不重复发 */
    fun requestReview(force: Boolean = false) {
        if (_aiReview.value is AiReview.Loading) return
        val state = ui.value
        val payload = buildReviewPayload(state)
        val fingerprint = payload.hashCode().toLong()
        val cached = _aiReview.value
        if (!force && cached is AiReview.Ready && cached.fingerprint == fingerprint) return
        _aiReview.value = AiReview.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val settings = apiSettings.value
            val ids = state.activeRules.map { it.id }
            val result: AiReview = runCatching { aiClient.reviewSummary(settings, payload) }
                .fold({ AiReview.Ready(it, ids, fingerprint) }, { AiReview.Failed(it.message ?: "请求失败") })
            kotlinx.coroutines.withContext(Dispatchers.Main) { _aiReview.value = result }
        }
    }

    /** 发给接口的数据描述：编号预案清单 + 带时间的打卡流水（流水只引用编号） */
    private fun buildReviewPayload(s: AppUiState): String {
        val rules = s.activeRules
        val indexOf = rules.withIndex().associate { (i, r) -> r.id to i + 1 }
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        return buildString {
            append("时间范围：${s.reviewWindow.noun}。窗口内打卡 ${s.totalDone} 次，其中两分钟版 ${s.miniDone} 次。\n")
            append("预案清单：\n")
            rules.forEachIndexed { i, r ->
                val st = s.stats[r.id] ?: RuleStat()
                append("${i + 1}. 如果 ${r.ifText} → 就 ${r.thenText}（触发 ${st.triggers} 次，做了 ${st.done} 次）\n")
            }
            s.chain.health.forEach { h ->
                append("链条「${h.title}」共 ${h.size} 步，${h.activeDays} 天里有动静，整条走完 ${h.fullDays} 天")
                append(if (h.worstStep > 0) "，最常断在第 ${h.worstStep} 步\n" else "，没有断点\n")
            }
            s.worst?.let { append("执行率最低的一条：「${it.rule.ifText}」做了 ${it.done}/${it.triggers}\n") }
            val logs = s.heat.cells.flatMap { it.logs }
                .sortedByDescending { it.createdAt }
                .take(Constants.AI_REVIEW_MAX_LOGS)
            append("打卡流水（最近 ${logs.size} 条，倒序，#n 是上面的预案编号）：\n")
            logs.forEach { l ->
                val verdict = when (l.done) {
                    true -> "做了" + if (l.mini) "（两分钟版）" else ""
                    false -> "没做"
                    null -> "待确认"
                }
                append("${fmt.format(Date(l.createdAt))} #${indexOf[l.ruleId] ?: 0} $verdict\n")
            }
        }
    }

    fun testApi(s: ApiSettings, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching { aiClient.testConnection(s) }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(r) }
        }
    }

    fun checkUpdate(currentVersion: String, onResult: (Result<UpdateInfo>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching { updateChecker.check(currentVersion) }
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
