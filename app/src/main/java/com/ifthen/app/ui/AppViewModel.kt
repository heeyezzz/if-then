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
import com.ifthen.app.data.ExecLog
import com.ifthen.app.data.Folder
import com.ifthen.app.data.Rule
import com.ifthen.app.data.RuleStatus
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

data class FolderSummary(
    val folder: Folder,
    val depth: Int,
    val triggers30: Int,
    val done30: Int
)

data class AppUiState(
    val activeRules: List<Rule> = emptyList(),
    val archivedRules: List<Rule> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val currentFolderId: Long? = null,
    val crumbs: List<Folder> = emptyList(),
    val visibleFolders: List<Folder> = emptyList(),
    val visibleRules: List<Rule> = emptyList(),
    val frequentRuleIds: List<Long> = emptyList(),
    val stats: Map<Long, RuleStat> = emptyMap(),
    val folderSummaries: List<FolderSummary> = emptyList(),
    val todayLogs: List<ExecLog> = emptyList(),
    val heatmap: List<Int> = List(Constants.HEATMAP_DAYS) { 0 },
    val zombies: List<Rule> = emptyList(),
    val highTriggers: List<Rule> = emptyList(),
    val ruleById: Map<Long, Rule> = emptyMap()
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
        val active = rules.filter { it.status == RuleStatus.ACTIVE.name }
        val archived = rules.filter { it.status != RuleStatus.ACTIVE.name }
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

        return AppUiState(
            activeRules = active,
            archivedRules = archived,
            folders = folders,
            currentFolderId = folderId,
            crumbs = crumbs,
            visibleFolders = visibleFolders,
            visibleRules = visibleRules,
            frequentRuleIds = frequent,
            stats = stats,
            folderSummaries = summaries,
            todayLogs = todayLogs,
            heatmap = heat,
            zombies = zombies,
            highTriggers = high,
            ruleById = rules.associateBy { it.id }
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

    fun logDone(ruleId: Long) = viewModelScope.launch(Dispatchers.IO) {
        db.execLogDao().insert(ExecLog(ruleId = ruleId, done = true))
    }

    fun logNotDone(ruleId: Long) = viewModelScope.launch(Dispatchers.IO) {
        db.execLogDao().insert(ExecLog(ruleId = ruleId, done = false))
    }

    /** 晚上补记：先记一条「待定」，之后再标做了/没做 */
    fun logPending(ruleId: Long) = viewModelScope.launch(Dispatchers.IO) {
        db.execLogDao().insert(ExecLog(ruleId = ruleId, done = null))
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
        db.ruleDao().update(
            rule.copy(status = if (archived) RuleStatus.ARCHIVED.name else RuleStatus.ACTIVE.name)
        )
    }

    fun deleteRule(rule: Rule) = viewModelScope.launch(Dispatchers.IO) {
        db.ruleDao().delete(rule)
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

    fun addCandidates(candidates: List<AiCandidate>, folderId: Long?) = viewModelScope.launch(Dispatchers.IO) {
        var order = db.ruleDao().maxSortOrder(folderId) + 1
        candidates.forEach { c ->
            db.ruleDao().upsert(
                Rule(ifText = c.ifText, thenText = c.thenText, folderId = folderId, sortOrder = order++)
            )
        }
    }

    // ---- 文件夹动作 ----

    fun openFolder(id: Long?) {
        currentFolderId.value = id
    }

    fun createFolder(name: String, emoji: String, color: Long) = viewModelScope.launch(Dispatchers.IO) {
        db.folderDao().upsert(
            Folder(name = name.trim(), emoji = emoji, color = color, parentId = currentFolderId.value)
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
