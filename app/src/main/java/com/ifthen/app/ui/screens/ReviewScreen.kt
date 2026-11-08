package com.ifthen.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ifthen.app.Constants
import com.ifthen.app.ReviewWindow
import com.ifthen.app.data.ExecLog
import com.ifthen.app.data.Rule
import com.ifthen.app.ui.AiReview
import com.ifthen.app.ui.AppUiState
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.ui.ChainHealth
import com.ifthen.app.ui.ChainState
import com.ifthen.app.ui.RuleGroup
import com.ifthen.app.ui.RuleStat
import com.ifthen.app.ui.TodayState
import com.ifthen.app.ui.TodayStatus
import com.ifthen.app.ui.components.ActionMiss
import com.ifthen.app.ui.components.Heatmap
import com.ifthen.app.ui.components.SectionHeader
import com.ifthen.app.ui.components.SemanticTag
import com.ifthen.app.ui.components.StreakChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("HH:mm", Locale.CHINA)

@Composable
fun ReviewScreen(
    vm: AppViewModel,
    state: AppUiState,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPickRule by rememberSaveable { mutableStateOf(false) }
    var openTrend by rememberSaveable { mutableStateOf(true) }
    var openArchived by rememberSaveable { mutableStateOf(false) }
    var openRanked by rememberSaveable { mutableStateOf(false) }
    var worstFirst by rememberSaveable { mutableStateOf(true) }
    var heatCell by rememberSaveable { mutableStateOf(-1) }
    var deleteTarget by rememberSaveable { mutableStateOf(0L) }
    val ai by vm.aiReview.collectAsStateWithLifecycle()

    // 分组在 composable 作用域里算一次并缓存：留在 LazyListScope 里每次重组都会重跑
    // sanitizedNext + O(n^2) 前驱查找，尖峰全落在 Compose 的动画回调阶段
    val plan = remember(state.folderSummaries, state.activeRules) {
        state.folderSummaries.map { fs ->
            fs to ChainState.groupsOf(
                state.activeRules.filter { it.folderId == fs.folder.id }
            )
        } to ChainState.groupsOf(
            state.activeRules.filter { it.folderId == null }
        )
    }
    val logGroups = remember(state.todayLogs, state.chain.chainPos, state.ruleById) {
        groupTodayLogs(state)
    }
    // 最该改档：执行率升序，样本不够的沉底；同率按触发次数多的排前
    val ranked = remember(state.activeRules, state.stats, state.reviewWindow) {
        state.activeRules.sortedWith(
            compareBy<Rule> { state.stats[it.id]?.rate(state.reviewWindow) ?: Int.MAX_VALUE }
                .thenByDescending { state.stats[it.id]?.triggers ?: 0 }
        )
    }
    val openLogGroups = remember { mutableStateMapOf<Long, Boolean>() }

    // 长按任意节头 = 整页收纳/展开。结论卡和 AI 块不参与，收了这页就没结论了
    val anySectionOpen = openTrend || openArchived || openRanked ||
        logGroups.any { openLogGroups[it.key] != false }
    fun setAllSections(expand: Boolean) {
        openTrend = expand
        openArchived = expand
        openRanked = expand
        logGroups.forEach { openLogGroups[it.key] = expand }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "title") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "复盘",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )
                    WindowPicker(state.reviewWindow) { vm.setReviewWindow(it) }
                }
            }

            item(key = "conclusion") {
                InsightCard(state = state, ai = ai, onEdit = onEdit) { vm.requestReview(it) }
            }

            item(key = "h-today") {
                SectionHeader(
                    title = "今日流水",
                    action = { TextButton(onClick = { showPickRule = true }) { Text("补记触发") } }
                )
            }
            if (state.todayLogs.isEmpty()) {
                item(key = "today-empty") {
                    Text(
                        "今天还没有记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            logGroups.forEach { g ->
                item(key = "lg${g.key}") {
                    LogGroupCard(
                        group = g,
                        ruleById = state.ruleById,
                        expanded = openLogGroups[g.key] != false,
                        onToggle = { openLogGroups[g.key] = !(openLogGroups[g.key] ?: true) },
                        onLongPressAll = { setAllSections(!anySectionOpen) },
                        onResolve = { log, done -> vm.resolveLog(log, done) },
                        onLongPress = { deleteTarget = it.id }
                    )
                }
            }

            item(key = "h-trend") {
                CollapsibleHeader(
                    "趋势 · ${state.reviewWindow.noun}", openTrend,
                    summary = "${state.totalDone} 次 · 今天 ${state.heat.cells.lastOrNull()?.count ?: 0}",
                    icon = Icons.Filled.Insights,
                    onToggle = { openTrend = !openTrend },
                    onLongPress = { setAllSections(!anySectionOpen) }
                )
            }
            if (openTrend) {
                item(key = "trend") {
                    TrendCard(
                        state = state,
                        selected = heatCell,
                        onCell = { heatCell = if (heatCell == it) -1 else it }
                    )
                }
            }

            item(key = "h-all") {
                CollapsibleHeader(
                    title = "全部战绩",
                    expanded = openRanked,
                    summary = rankedSummary(state),
                    icon = Icons.Filled.Leaderboard,
                    action = if (!openRanked) null else {
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(onClick = { worstFirst = true }) {
                                    Text(
                                        "最该改",
                                        fontWeight = if (worstFirst) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (worstFirst) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { worstFirst = false }) {
                                    Text(
                                        "文件夹",
                                        fontWeight = if (!worstFirst) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (!worstFirst) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    onToggle = { openRanked = !openRanked },
                    onLongPress = { setAllSections(!anySectionOpen) }
                )
            }
            if (openRanked && worstFirst) {
                items(ranked, key = { "w${it.id}" }) { rule ->
                    val st = state.stats[rule.id] ?: RuleStat()
                    StatRow(
                        title = rule.ifText,
                        detail = detailText(st, state.reviewWindow) + " · " + folderLabel(state, rule.folderId),
                        streak = st.streak,
                        today = state.todayStatusByRule[rule.id],
                        onClick = { onEdit(rule.id) }
                    )
                }
            } else if (openRanked) {
                plan.first.forEach { (s, groups) ->
                    item(key = "fs${s.folder.id}") {
                        Row(
                            Modifier.padding(start = (s.depth * 16).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${s.folder.emoji}${s.folder.name}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "触发 ${s.triggers} · " + rateText(s.rate(state.reviewWindow)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    ruleItemsWithChains(groups = groups, state = state, onEdit = onEdit)
                }
                run {
                    val rootRules = state.activeRules.filter { it.folderId == null }
                    if (rootRules.isNotEmpty() || state.folderSummaries.isNotEmpty()) {
                        item(key = "fsroot") {
                            Text(
                                "🗂 根目录",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        ruleItemsWithChains(groups = plan.second, state = state, onEdit = onEdit)
                    }
                }
            }

            if (state.archivedRules.isNotEmpty()) {
                item(key = "h-archived") {
                    CollapsibleHeader(
                        "已归档 · ${state.archivedRules.size} 条", openArchived,
                        icon = Icons.Filled.Archive,
                        onToggle = { openArchived = !openArchived },
                        onLongPress = { setAllSections(!anySectionOpen) }
                    )
                }
                if (openArchived) {
                    items(state.archivedRules, key = { "r${it.id}" }) { rule ->
                        ZombieRow(
                            "【归档】${rule.ifText}", rule.thenText,
                            onArchive = { vm.setArchived(rule, false) },
                            onEdit = { onEdit(rule.id) },
                            archiveLabel = "恢复"
                        )
                    }
                }
            }
            item(key = "bottom-gap") { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (deleteTarget != 0L) {
        val log = state.todayLogs.firstOrNull { it.id == deleteTarget }
        AlertDialog(
            onDismissRequest = { deleteTarget = 0L },
            title = { Text("删除这条记录？") },
            text = {
                Text(
                    "如果 " + (state.ruleById[log?.ruleId]?.ifText ?: "（已删除的预案）") +
                        "，" + (log?.let { timeFmt.format(Date(it.createdAt)) } ?: "") + "。删掉后这条打卡不再计入任何统计。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    log?.let { vm.deleteLog(it) }
                    deleteTarget = 0L
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = 0L }) { Text("取消") } }
        )
    }

    if (showPickRule) {
        BackfillDialog(state, onDismiss = { showPickRule = false }) { vm.logPending(it) }
    }
}

// ---- 顶部：时间范围 / 结论 / AI ----

@Composable
private fun WindowPicker(current: ReviewWindow, onPick: (ReviewWindow) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ReviewWindow.entries.forEach { w ->
            val sel = w == current
            Text(
                w.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                color = if (sel) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .semantics { contentDescription = "统计范围切到 ${w.label}" }
                    .clickable { onPick(w) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/** 结论卡：体检结论 + AI 补一句（合并成一张卡，AI 作为卡内一条轻量补充） */
@Composable
private fun InsightCard(
    state: AppUiState,
    ai: AiReview,
    onEdit: (Long) -> Unit,
    onRequest: (Boolean) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
            Text(
                "体检结论 · ${state.reviewWindow.noun}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            val worst = state.worst
            val bp = state.breakPoint
            if (worst == null && bp == null) {
                Text(
                    "样本还不够，先记几天。窗口里没有一条预案够得上 ${state.reviewWindow.minSample} 次触发。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
            worst?.let {
                InsightRow(
                    text = if (it.rate >= 100)
                        "「${it.rule.ifText}」触发 ${it.triggers} 次全做了，窗口里没有短板"
                    else
                        "最该改的是「${it.rule.ifText}」：${it.done}/${it.triggers} 次，执行率 ${it.rate}%",
                    onEdit = { onEdit(it.rule.id) }
                )
            }
            bp?.let { h ->
                val stepRuleId = h.stepRuleIds.getOrElse(h.worstStep - 1) { h.headId }
                InsightRow(
                    text = "「${h.title}」这条链最常断在第 ${h.worstStep} 步 — 那一步在现实中接不上",
                    onEdit = { onEdit(stepRuleId) }
                )
            }

            // AI：合并进结论卡，不再单独占一张卡
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                when (ai) {
                    AiReview.Idle -> {
                        Text(
                            "让 AI 补一句（参考）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { onRequest(false) },
                            enabled = state.activeRules.isNotEmpty()
                        ) { Text("生成") }
                    }
                    AiReview.Loading -> {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "AI 在读你的 ${state.totalDone} 次打卡",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    is AiReview.Failed -> {
                        Text(
                            ai.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onRequest(true) }) { Text("重试") }
                    }
                    is AiReview.Ready -> {
                        Text(
                            "AI 复盘（参考）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onRequest(true) }) { Text("重算") }
                    }
                }
            }
            if (ai is AiReview.Ready) {
                Text(
                    ai.insight.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                ai.insight.suggestions.forEach { s ->
                    val ruleId = ai.ruleIds.getOrNull(s.rule - 1)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(if (ruleId != null) Modifier.clickable { onEdit(ruleId) } else Modifier)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("▸", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            s.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (ruleId != null) Text(
                            "去改",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightRow(text: String, onEdit: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onEdit() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "去改",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ---- 今日流水 ----

private data class LogGroup(
    val key: Long,
    val at: Long,
    val title: String,
    val stepOf: Map<Long, Int>,
    val total: Int,
    val logs: List<ExecLog>
)

/** 今天同一条链的打卡归成一组；不在链上的单独成组 */
private fun groupTodayLogs(state: AppUiState): List<LogGroup> {
    val byKey = LinkedHashMap<Long, ArrayList<ExecLog>>()
    state.todayLogs.forEach { l ->
        val key = state.chain.chainPos[l.ruleId]?.headId ?: l.ruleId
        byKey.getOrPut(key) { ArrayList() }.add(l)
    }
    return byKey.map { (key, logs) ->
        val head = state.ruleById[key]
        val pos = state.chain.chainPos
        val size = pos[key]?.size ?: 1
        val ordered = if (size > 1) {
            logs.sortedBy { pos[it.ruleId]?.step ?: Int.MAX_VALUE }
        } else logs.sortedByDescending { it.createdAt }
        LogGroup(
            key = key,
            at = logs.maxOf { it.createdAt },
            title = head?.ifText ?: "（已删除的预案）",
            stepOf = if (size >= Constants.LOG_CHAIN_MIN_SIZE)
                logs.mapNotNull { l -> pos[l.ruleId]?.let { l.ruleId to it.step } }.toMap()
            else emptyMap(),
            total = if (size >= Constants.LOG_CHAIN_MIN_SIZE) size else logs.size,
            logs = ordered
        )
    }.sortedByDescending { it.at }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogGroupCard(
    group: LogGroup,
    ruleById: Map<Long, Rule>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLongPressAll: () -> Unit,
    onResolve: (ExecLog, Boolean?) -> Unit,
    onLongPress: (ExecLog) -> Unit
) {
    val doneCount = group.logs.filter { it.done == true }.map { it.ruleId }.distinct().size
    val arrow by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(),
        label = "logGroupArrow"
    )
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onToggle, onLongClick = onLongPressAll)
                    .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${timeFmt.format(Date(group.at))}  ${group.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (group.total > 1) "这条链今天 $doneCount/${group.total} 步"
                        else if (doneCount == group.logs.size) "做了 ✓"
                        else "还没定论",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (group.total > 1 && doneCount < group.total)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "收起这组" else "展开这组",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp).rotate(arrow)
                )
            }
            if (expanded) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    group.logs.forEach { log ->
                        LogLine(
                            log = log,
                            ruleIf = ruleById[log.ruleId]?.ifText ?: "（已删除的预案）",
                            step = group.stepOf[log.ruleId],
                            onResolve = { done -> onResolve(log, done) },
                            onLongPress = { onLongPress(log) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogLine(
    log: ExecLog,
    ruleIf: String,
    step: Int?,
    onResolve: (Boolean?) -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            timeFmt.format(Date(log.createdAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
            modifier = Modifier.width(42.dp)
        )
        if (step != null) {
            Text(
                "$step",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                    .padding(horizontal = 5.dp)
            )
        }
        Text(
            ruleIf + if (log.backfill) "  · 补" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        when (log.done) {
            true -> Text(
                "做了 ✓", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            false -> Text(
                "没做", style = MaterialTheme.typography.labelMedium, color = ActionMiss
            )
            null -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onResolve(true) }) { Text("做了") }
                TextButton(onClick = { onResolve(false) }) { Text("没做") }
            }
        }
    }
}

// ---- 趋势 / 折叠标题 ----

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollapsibleHeader(
    title: String,
    expanded: Boolean,
    summary: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
    onToggle: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        if (!expanded && !summary.isNullOrBlank()) {
            Text(
                summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.weight(1f))
        if (expanded) action?.invoke()
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) "收起$title" else "展开$title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .rotate(if (expanded) 90f else 0f)
        )
    }
}

@Composable
private fun TrendCard(
    state: AppUiState,
    selected: Int,
    onCell: (Int) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Heatmap(state.heat, selected, onCell)
            if (state.totalDone > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.miniDone == 0) "${state.reviewWindow.noun} ${state.totalDone} 次打卡，每次都做了完整版"
                    else "${state.reviewWindow.noun} ${state.totalDone} 次打卡，其中 ${state.miniDone} 次只做两分钟版" +
                        " — 动作没断就是赢",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected in state.heat.cells.indices) {
                val cell = state.heat.cells[selected]
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        cell.label + " · 做了 ${cell.count} 次",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (cell.logs.isEmpty()) {
                        Text(
                            "这段时间没有记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    cell.logs.sortedByDescending { it.createdAt }.take(30).forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                timeFmt.format(Date(l.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(42.dp)
                            )
                            Text(
                                state.ruleById[l.ruleId]?.ifText ?: "（已删除的预案）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                when (l.done) {
                                    true -> "✓"
                                    false -> "✗"
                                    null -> "…"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when (l.done) {
                                    true -> MaterialTheme.colorScheme.primary
                                    false -> ActionMiss
                                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- 链条健康 / 一次性 / 战绩行 ----

/** 链条健康：窗口内走通次数 + 最常断在第几步 */
@Composable
private fun ChainHealthRow(h: ChainHealth, onEditStep: (Long) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "走通 ${h.fullDays}/${h.activeDays} 天",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (h.fullDays > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary
                )
            }
            // 没有「没做」记录时柱子全是平的，画出来像坏了，直接留一句话
            if (h.breakAt.any { it > 0 }) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(30.dp)
                ) {
                    h.breakAt.forEachIndexed { i, c ->
                        val stepRuleId = h.stepRuleIds.getOrElse(i) { h.headId }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onEditStep(stepRuleId) }
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height((4 + c * 6).dp.coerceAtMost(22.dp))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        MaterialTheme.colorScheme.primary
                                            .copy(alpha = if (c == 0) 0.16f else 0.62f)
                                    )
                            )
                            Text(
                                circled(i + 1),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (i + 1 == h.worstStep) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Text(
                if (h.worstStep > 0) "最常断在第 ${h.worstStep} 步 — 点那根柱子直接去改那一步"
                else "${h.activeDays} 天里没有断点",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OneOffCard(
    s: com.ifthen.app.ui.OneOffStats,
    tab: String,
    onPick: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OneOffStat("完成", s.done, tab, "done", onPick, MaterialTheme.colorScheme.primary)
                OneOffStat("落空", s.missed, tab, "missed", onPick, MaterialTheme.colorScheme.tertiary)
                OneOffStat("待办", s.pending, tab, "pending", onPick, MaterialTheme.colorScheme.onSurface)
                OneOffStat("逾期", s.overdue, tab, "overdue", onPick, MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                s.rate?.let { "近 30 天完成率 $it% — 订了又没做的越少，说明预案订得越准" }
                    ?: "近 30 天还没有结清记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val picked = when (tab) {
                "done" -> s.doneRules
                "missed" -> s.missedRules
                "pending" -> s.pendingRules
                "overdue" -> s.overdueRules
                else -> emptyList()
            }
            if (picked.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                picked.forEach { r ->
                    Text(
                        "· ${r.ifText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun OneOffStat(
    label: String,
    value: Int,
    tab: String,
    key: String,
    onPick: (String) -> Unit,
    color: Color
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onPick(key) }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "$value",
            style = MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = if (tab == key) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            label + if (value > 0) " ▾" else "",
            style = MaterialTheme.typography.labelSmall,
            color = if (tab == key) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun LazyListScope.ruleItemsWithChains(
    groups: List<RuleGroup>,
    state: AppUiState,
    onEdit: (Long) -> Unit
) {
    groups.forEach { g ->
        val head = g.rules.first()
        val today = state.chain.todayByHead[head.id]
        val chained = g.rules.size > 1
        if (chained && today != null) {
            item(key = "cl${head.id}") {
                Text(
                    "链 · ${today.size} 步 今天 ${today.doneToday}/${today.size}" +
                        if (today.doneToday == today.size) " ✅" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 18.dp, top = 4.dp)
                )
            }
        }
        g.rules.forEachIndexed { i, rule ->
            item(key = "s${rule.id}") {
                val st = state.stats[rule.id] ?: RuleStat()
                val step = state.chain.chainPos[rule.id]?.step ?: (i + 1)
                StatRow(
                    title = (if (chained) "${circled(step)} " else "") + rule.ifText,
                    detail = detailText(st, state.reviewWindow),
                    streak = st.streak,
                    today = state.todayStatusByRule[rule.id],
                    onClick = { onEdit(rule.id) }
                )
            }
        }
        g.offFolderTails.forEach { t ->
            item(key = "xt${t.id}") {
                Text(
                    "↳ 后续「${t.ifText}」在别的文件夹里",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 22.dp)
                )
            }
        }
    }
}

private val circledGlyphs = listOf("①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧")

private fun circled(n: Int): String = circledGlyphs.getOrElse(n - 1) { "$n." }

private fun rateText(rate: Int?): String = if (rate == null) "样本少" else "执行率 $rate%"

private fun detailText(st: RuleStat, window: ReviewWindow): String {
    if (st.triggers == 0) return "${window.noun}没触发"
    return "${window.noun}触发 ${st.triggers} · " + rateText(st.rate(window))
}

/** 收起「全部战绩」时节头上那句摘要 */
private fun rankedSummary(state: AppUiState): String {
    val n = state.activeRules.size
    val w = state.worst ?: return "$n 条 · 都样本少"
    return "$n 条 · 最低 ${w.rate}%"
}

private fun folderLabel(state: AppUiState, folderId: Long?): String {
    if (folderId == null) return "根目录"
    val byId = state.folders.associateBy { it.id }
    val names = ArrayList<String>()
    var cur = byId[folderId]
    var guard = 0
    while (cur != null && guard++ < 10) {
        names.add(0, cur.name)
        cur = cur.parentId?.let { byId[it] }
    }
    return if (names.isEmpty()) "根目录" else names.joinToString("/")
}

@Composable
private fun StatRow(
    title: String,
    detail: String,
    streak: Int,
    today: TodayStatus?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (today != null && today.state != TodayState.NONE) TodayDot(today)
            StreakChip(streak)
        }
    }
}

/** 复盘列表里的「今天」状态点：实心=已定论，空心=待确认 */
@Composable
private fun TodayDot(today: TodayStatus) {
    val (color, filled, label) = when (today.state) {
        TodayState.DONE -> Triple(MaterialTheme.colorScheme.primary, true, "今天做了")
        TodayState.MISSED -> Triple(ActionMiss, true, "今天没做")
        else -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, false, "今天待确认")
    }
    Box(
        Modifier
            .padding(end = 8.dp)
            .size(9.dp)
            .clip(CircleShape)
            .background(if (filled) color else Color.Transparent)
            .then(if (filled) Modifier else Modifier.border(1.5.dp, color, CircleShape))
            .semantics { contentDescription = label }
    )
}

@Composable
private fun ZombieRow(
    title: String,
    then: String,
    onArchive: () -> Unit,
    onEdit: () -> Unit,
    archiveLabel: String = "归档"
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "→ $then",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onArchive) { Text(archiveLabel) }
            TextButton(onClick = onEdit) { Text("查看") }
        }
    }
}

// ---- 补记：按文件夹分组 + 搜索 ----

@Composable
private fun BackfillDialog(state: AppUiState, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    var q by remember { mutableStateOf("") }
    val byId = state.folders.associateBy { it.id }
    fun path(id: Long?): String {
        if (id == null) return "🗂 根目录"
        val f = byId[id] ?: return "🗂 根目录"
        return path(f.parentId) + " / " + f.emoji + f.name
    }
    val grouped = remember(state.activeRules, q) {
        val hit = state.activeRules.filter {
            q.isBlank() || it.ifText.contains(q, true) || it.thenText.contains(q, true)
        }
        hit.groupBy { path(it.folderId) }.toList().sortedBy { it.first }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("补记哪条预案的触发？") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    placeholder = { Text("搜预案名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    grouped.forEach { (label, rules) ->
                        item(key = "bg$label") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SemanticTag("夹", com.ifthen.app.ui.components.TagTone.Action, modifier = Modifier.padding(end = 6.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(rules, key = { "b${it.id}" }) { rule ->
                            TextButton(
                                onClick = { onPick(rule.id); onDismiss() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("如果 " + rule.ifText, maxLines = 1)
                            }
                        }
                    }
                    if (grouped.isEmpty()) {
                        item {
                            Text(
                                "没有匹配的预案。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
