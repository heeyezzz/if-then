package com.ifthen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import com.ifthen.app.data.ExecLog
import com.ifthen.app.data.Rule
import com.ifthen.app.ui.AppUiState
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.ui.ChainHealth
import com.ifthen.app.ui.ChainState
import com.ifthen.app.ui.RuleStat
import com.ifthen.app.ui.components.Heatmap
import com.ifthen.app.ui.components.SectionHeader
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
    var showPickRule by remember { mutableStateOf(false) }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("复盘", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 8.dp))
            }

            item {
                SectionHeader(
                    title = "今日流水",
                    action = {
                        TextButton(onClick = { showPickRule = true }) { Text("补记触发") }
                    }
                )
            }
            if (state.todayLogs.isEmpty()) {
                item {
                    Text("今天还没有记录。", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.todayLogs, key = { "t${it.id}" }) { log ->
                androidx.compose.foundation.layout.Box(Modifier.animateItem()) {
                TodayLogRow(
                    log = log,
                    ruleIf = state.ruleById[log.ruleId]?.ifText ?: "（已删除的预案）",
                    onResolve = { done -> vm.resolveLog(log, done) },
                    onDelete = { vm.deleteLog(log) }
                )
                }
            }

            item { SectionHeader("最近 30 天") }
            item { Card(Modifier.fillMaxWidth()) { Heatmap(state.heatmap, Modifier.padding(12.dp)) } }

            if (state.zombies.isNotEmpty()) {
                item { SectionHeader("僵尸预案") }
                items(state.zombies, key = { "z${it.id}" }) { rule ->
                    ZombieRow(rule.ifText, rule.thenText, onArchive = { vm.setArchived(rule, true) },
                        onEdit = { onEdit(rule.id) })
                }
            }

            if (state.highTriggers.isNotEmpty()) {
                item { SectionHeader("最常触发") }
                items(state.highTriggers, key = { "h${it.id}" }) { rule ->
                    val s = state.stats[rule.id] ?: RuleStat()
                    StatRow(rule.ifText, "30天触发 ${s.triggers30} 次 · 执行率 ${rateText(s)}", s.streak)
                }
            }

            if (state.chain.health.isNotEmpty()) {
                item { SectionHeader("链条健康 · 近 14 天") }
                items(state.chain.health, key = { "c${it.headId}" }) { h ->
                    ChainHealthRow(h)
                }
            }

            item { SectionHeader("全部战绩") }
            state.folderSummaries.forEach { s ->
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
                            "触发 ${s.triggers30} · 执行率 " +
                                if (s.triggers30 == 0) "—" else "${s.done30 * 100 / s.triggers30}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ruleItemsWithChains(
                    inFolder = state.activeRules.filter { it.folderId == s.folder.id },
                    state = state
                )
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
                    ruleItemsWithChains(inFolder = rootRules, state = state)
                }
            }

            if (state.archivedRules.isNotEmpty()) {
                item { SectionHeader(title = "已归档") }
                items(state.archivedRules, key = { "r${it.id}" }) { rule ->
                    ZombieRow("【归档】${rule.ifText}", rule.thenText,
                        onArchive = { vm.setArchived(rule, false) }, onEdit = { onEdit(rule.id) },
                        archiveLabel = "恢复")
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showPickRule) {
        AlertDialog(
            onDismissRequest = { showPickRule = false },
            title = { Text("补记哪条预案的触发？") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    state.activeRules.forEach { rule ->
                        TextButton(
                            onClick = {
                                vm.logPending(rule.id)
                                showPickRule = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("如果 " + rule.ifText)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPickRule = false }) { Text("取消") } }
        )
    }
}

/** 全部战绩里的预案条目：链条成员聚在一起，头部带今日进度 */
private fun LazyListScope.ruleItemsWithChains(
    inFolder: List<Rule>,
    state: AppUiState
) {
    ChainState.groupsOf(inFolder, state.activeRules).forEach { g ->
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
                    "  " + (if (chained) "${circled(step)} " else "") + rule.ifText,
                    "30天触发 ${st.triggers30} · 执行率 ${rateText(st)}",
                    st.streak
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

private fun circled(n: Int): String =
    if (n in 1..8) "①②③④⑤⑥⑦⑧"[n - 1].toString() else "$n."

/** 链条健康：近 14 天走通次数 + 最常断在第几步 */
@Composable
private fun ChainHealthRow(h: ChainHealth) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
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
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(30.dp)
            ) {
                h.breakAt.forEachIndexed { i, c ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
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
            Text(
                if (h.worstStep > 0) "最常断在第 ${h.worstStep} 步 — 那一步在现实中接不上，考虑改掉它"
                else "14 天里没有断点",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun rateText(s: RuleStat): String =
    if (s.triggers30 == 0) "—" else "${s.done30 * 100 / s.triggers30}%"

@Composable
private fun TodayLogRow(log: ExecLog, ruleIf: String, onResolve: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(timeFmt.format(Date(log.createdAt)) + "  如果 " + ruleIf,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                when (log.done) {
                    true -> Text("做了 ✓", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    false -> Text("没做", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary)
                    null -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onResolve(true) }) { Text("做了") }
                        OutlinedButton(onClick = { onResolve(false) }) { Text("没做") }
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除这条记录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatRow(title: String, detail: String, streak: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StreakChip(streak)
        }
    }
}

@Composable
private fun ZombieRow(
    title: String,
    then: String,
    onArchive: () -> Unit,
    onEdit: () -> Unit,
    archiveLabel: String = "归档"
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text("→ $then", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onArchive) { Text(archiveLabel) }
            TextButton(onClick = onEdit) { Text("查看") }
        }
    }
}
