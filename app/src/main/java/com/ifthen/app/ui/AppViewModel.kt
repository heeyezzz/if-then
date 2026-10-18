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
        combine(now, currentFolderId, reviewWindow) { t, f, w -> Triple(t, f, w) }
    ) { rules, logs, folders, extra ->
        buildState(rules, logs, folders, extra.first, extra.second, extra.third)
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

    /** 链条整串保存：一行一步，id=0 是还没入库的新步骤；broken 表示这一步与前一步之间断开 */
    data class ChainStep(val id: Long, val ifText: String, val thenText: String, val broken: Boolean)

    /** 读一条链作为整串编辑的初值 */
    suspend fun chainSteps(headId: Long): List<ChainStep> = withContext(Dispatchers.IO) {
        val active = db.ruleDao().observeAll().first()
            .filter { it.status == RuleStatus.ACTIVE.name && !it.isOneOff }
        val next = Chain.sanitizedNext(active)
        Chain.walk(headId, next, active.associateBy { it.id }).map {
            ChainStep(it.id, it.ifText, it.thenText, false)
        }
    }

    /** 可作为「换成已有的」的候选：活跃长期预案，排除已在本串里的；带上它现有的链位置供界面标注 */
    suspend fun chainPickable(inUse: Set<Long>): List<Pair<Rule, ChainPos?>> =
        withContext(Dispatchers.IO) {
            val active = db.ruleDao().observeAll().first()
                .filter { it.status == RuleStatus.ACTIVE.name && !it.isOneOff }
            val pos = HashMap<Long, ChainPos>()
            Chain.chains(active).forEach { path ->
                path.forEachIndexed { i, r -> pos[r.id] = ChainPos(path.first().id, i + 1, path.size) }
            }
            active.filter { it.id !in inUse }
                .map { it to pos[it.id] }
                .sortedWith(
                    compareBy({ it.second?.headId ?: -1L }, { it.second?.step ?: 0 })
                )
        }

    fun saveChain(steps: List<ChainStep>, folderId: Long?, onResult: (String) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            val ids = ArrayList<Long>()
            val kept = ArrayList<ChainStep>()
            steps.forEach { s ->
                val ifText = s.ifText.trim(); val thenText = s.thenText.trim()
                if (s.id > 0L) {
                    val r = db.ruleDao().getById(s.id) ?: return@forEach
                    if (r.ifText != ifText || r.thenText != thenText || r.folderId != folderId) {
                        db.ruleDao().update(r.copy(ifText = ifText, thenText = thenText, folderId = folderId))
                    }
                    ids.add(r.id); kept.add(s.copy(ifText = ifText, thenText = thenText))
                } else if (ifText.isNotBlank() && thenText.isNotBlank()) {
                    val id = db.ruleDao().upsert(
                        Rule(
                            ifText = ifText, thenText = thenText, folderId = folderId,
                            sortOrder = db.ruleDao().maxSortOrder(folderId) + 1,
                            kind = RuleKind.REPEATING.name
                        )
                    )
                    ids.add(id); kept.add(s.copy(ifText = ifText, thenText = thenText))
                }
            }
            // 先清掉所有牵涉这批节点的边（含别人指向它们的边），再按行序重接：幂等，不怕中间态
            val set = ids.toSet()
            db.ruleDao().observeAll().first().forEach { r ->
                val n = r.nextRuleId
                if (n != null && (r.id in set || n in set)) db.ruleDao().updateNext(r.id, null)
            }
            var start = 0
            (kept.indices.filter { kept[it].broken && it > 0 } + kept.size).forEach { mark ->
                for (i in start until mark - 1) db.ruleDao().updateNext(ids[i], ids[i + 1])
                start = mark
            }
            val chains = kept.indices.count { kept[it].broken && it > 0 } + 1
            withContext(Dispatchers.Main) {
                onResult(if (chains > 1) "已保存 ${ids.size} 步，拆成 $chains 段" else "已保存 ${ids.size} 步链")
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
