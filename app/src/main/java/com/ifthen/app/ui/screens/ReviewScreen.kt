package com.ifthen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import com.ifthen.app.data.ExecLog
import com.ifthen.app.ui.AppUiState
import com.ifthen.app.ui.AppViewModel
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(
                        title = "今日流水",
                        subtitle = "睡前把「待定」补记完",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showPickRule = true }) { Text("补记触发") }
                }
            }
            if (state.todayLogs.isEmpty()) {
                item {
                    Text("今天还没有记录。", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.todayLogs, key = { "t${it.id}" }) { log ->
                TodayLogRow(
                    log = log,
                    ruleIf = state.ruleById[log.ruleId]?.ifText ?: "（已删除的预案）",
                    onResolve = { done -> vm.resolveLog(log, done) },
                    onDelete = { vm.deleteLog(log) }
                )
            }

            item { SectionHeader(title = "最近 30 天", subtitle = "颜色越深，当天顶住的次数越多") }
            item { Card(Modifier.fillMaxWidth()) { Heatmap(state.heatmap, Modifier.padding(12.dp)) } }

            if (state.zombies.isNotEmpty()) {
                item { SectionHeader(title = "僵尸预案", subtitle = "两周以上没触发过，考虑归档") }
                items(state.zombies, key = { "z${it.id}" }) { rule ->
                    ZombieRow(rule.ifText, rule.thenText, onArchive = { vm.setArchived(rule, true) },
                        onEdit = { onEdit(rule.id) })
                }
            }

            if (state.highTriggers.isNotEmpty()) {
                item { SectionHeader(title = "最常触发", subtitle = "这些场景是真实存在的战场") }
                items(state.highTriggers, key = { "h${it.id}" }) { rule ->
                    val s = state.stats[rule.id] ?: RuleStat()
                    StatRow(rule.ifText, "30天触发 ${s.triggers30} 次 · 执行率 ${rateText(s)}", s.streak)
                }
            }

            item { SectionHeader(title = "全部战绩") }
            items(state.activeRules, key = { "s${it.id}" }) { rule ->
                val s = state.stats[rule.id] ?: RuleStat()
                StatRow(rule.ifText, "30天触发 ${s.triggers30} · 执行率 ${rateText(s)}", s.streak)
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
