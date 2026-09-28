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
import com.ifthen.app.data.Rule
import com.ifthen.app.data.RuleStatus
import com.ifthen.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class RuleStat(
    val streak: Int = 0,
    val triggers30: Int = 0,
    val done30: Int = 0,
    val lastTriggerAt: Long? = null
)

data class AppUiState(
    val activeRules: List<Rule> = emptyList(),
    val archivedRules: List<Rule> = emptyList(),
    val frequentRuleIds: List<Long> = emptyList(),
    val stats: Map<Long, RuleStat> = emptyMap(),
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

    val ui: StateFlow<AppUiState> = combine(
        db.ruleDao().observeAll(),
        db.execLogDao().observeSince(0L),
        now
    ) { rules, logs, ts -> buildState(rules, logs, ts) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppUiState())

    init {
        viewModelScope.launch {
            while (true) {
                now.value = System.currentTimeMillis()
                kotlinx.coroutines.delay(60_000)
            }
        }
    }

    private fun buildState(rules: List<Rule>, logs: List<ExecLog>, ts: Long): AppUiState {
        val active = rules.filter { it.status == RuleStatus.ACTIVE.name }
        val archived = rules.filter { it.status != RuleStatus.ACTIVE.name }
        val byRule = logs.groupBy { it.ruleId }
        val dayMs = 24L * 3600_000
        val since30 = ts - Constants.RULE_STATS_WINDOW_DAYS * dayMs
        val sinceFrequent = ts - Constants.FREQUENT_WINDOW_DAYS * dayMs

        val stats = active.associateWith { r -> }.keys.associate { r ->
            val rl = byRule[r.id].orEmpty() // 已按 createdAt DESC
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

        return AppUiState(
            activeRules = active,
            archivedRules = archived,
            frequentRuleIds = frequent,
            stats = stats,
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

    // ---- 动作 ----

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

    fun saveRule(rule: Rule, onSaved: (Long) -> Unit = {}) = viewModelScope.launch(Dispatchers.IO) {
        val id = db.ruleDao().upsert(rule)
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

    fun addCandidates(candidates: List<AiCandidate>) = viewModelScope.launch(Dispatchers.IO) {
        candidates.forEach { c ->
            db.ruleDao().upsert(Rule(ifText = c.ifText, thenText = c.thenText))
        }
    }

    fun testApi(s: ApiSettings, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching { aiClient.testConnection(s) }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(r) }
        }
    }

    fun generateCandidates(concern: String, onResult: (Result<List<AiCandidate>>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = apiSettings.value
            val r = runCatching { aiClient.generateCandidates(settings, concern) }
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
