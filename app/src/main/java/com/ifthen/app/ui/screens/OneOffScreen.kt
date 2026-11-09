package com.ifthen.app.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ifthen.app.data.Rule
import com.ifthen.app.data.RuleStatus
import com.ifthen.app.ui.AppUiState
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.ui.RuleStat
import com.ifthen.app.ui.SuccessChime
import com.ifthen.app.ui.TodayStatus
import com.ifthen.app.ui.TodayState
import com.ifthen.app.ui.components.SectionHeader
import com.ifthen.app.ui.components.DrawerMenuButton
import com.ifthen.app.ui.components.SortPad
import com.ifthen.app.ui.components.SwipeRuleCard
import com.ifthen.app.ui.components.TagTone
import com.ifthen.app.ui.components.TaggedLine
import com.ifthen.app.ui.components.TitleBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dueFmt = SimpleDateFormat("M月d日", Locale.CHINA)
private val resolvedTimeFmt = SimpleDateFormat("HH:mm", Locale.CHINA)

@Composable
fun OneOffScreen(
    vm: AppViewModel,
    state: AppUiState,
    onEdit: (Long) -> Unit,
    onRecord: (String) -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var revealedId by remember { mutableStateOf<Long?>(null) }
    var sorting by remember { mutableStateOf(false) }
    var ruleToDelete by remember { mutableStateOf<Rule?>(null) }

    val rules = state.oneOffActive
    val resolved = state.oneOffResolvedToday

    androidx.activity.compose.BackHandler(enabled = sorting || revealedId != null) {
        if (sorting) sorting = false else revealedId = null
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) revealedId = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { revealedId = null }) }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { revealedId = null }) },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                TitleBar(
                    title = "一次性",
                    emoji = null,
                    leading = if (onOpenDrawer != null) ({ DrawerMenuButton(onOpenDrawer) }) else null,
                    onBack = null,
                    action = {
                        if (sorting) {
                            TextButton(onClick = { sorting = false }) { Text("完成") }
                        } else {
                            IconButton(onClick = {
                                revealedId = null
                                sorting = true
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.SwapVert,
                                    contentDescription = "排序",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )
            }

            if (rules.isEmpty() && resolved.isEmpty()) {
                item {
                    Text(
                        "没有待结清的一次性预案。想到「如果…就…」只做一次的，来这里记一条。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    )
                }
            }

            if (rules.isNotEmpty()) {
                item {
                    SectionHeader(
                        if (sorting) "待结清（长按箭头可连续移动）" else "待结清 ${rules.size} 条"
                    )
                }
                rules.forEachIndexed { i, rule ->
                    item(key = "o" + rule.id) {
                        val overdue = state.oneOffOverdueDays[rule.id]
                        Box(Modifier.animateItem()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    SwipeRuleCard(
                                        rule = rule,
                                        stat = RuleStat(),
                                        revealedId = revealedId,
                                        onReveal = { revealedId = it },
                                        swipeEnabled = !sorting,
                                        overdueDays = overdue,
                                        dueLabel = if (overdue == null) {
                                            rule.dueDate?.let { "${dueFmt.format(Date(it))} 前" }
                                        } else null,
                                        moveLabel = "改长期",
                                        moveIcon = Icons.Filled.Autorenew,
                                        onDone = {
                                            SuccessChime.play()
                                            vm.logDone(rule.id)
                                            onRecord("已结清 · " + rule.thenText)
                                        },
                                        onMiniDone = {
                                            SuccessChime.play()
                                            vm.logDone(rule.id, mini = true)
                                            onRecord("最小版也算做了 · " + rule.miniThen)
                                        },
                                        onMiss = {
                                            vm.logNotDone(rule.id)
                                            onRecord("已记为落空 · 情境只来一次")
                                        },
                                        onMove = { vm.convertKind(rule.id, false, onRecord) },
                                        onArchive = {
                                            vm.setArchived(rule, true)
                                            onRecord("已放弃这条一次性预案")
                                        },
                                        onDelete = { ruleToDelete = rule },
                                        onOpen = {
                                            if (revealedId != null) revealedId = null else onEdit(rule.id)
                                        }
                                    )
                                }
                                if (sorting) {
                                    SortPad(
                                        label = "${i + 1}",
                                        movable = true,
                                        canUp = i > 0,
                                        canDown = i < rules.lastIndex,
                                        onMove = { vm.moveRuleStep(rule.id, it) },
                                        onEdge = { top -> vm.moveRuleToEdge(rule.id, top) },
                                        onBlocked = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (resolved.isNotEmpty()) {
                item { SectionHeader("今天已结清") }
                items(resolved, key = { "r" + it.id }) { rule ->
                    Box(Modifier.animateItem()) {
                        ResolvedOneOffRow(
                            rule = rule,
                            today = state.todayStatusByRule[rule.id],
                            onClick = { onEdit(rule.id) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("删除这条预案？") },
            text = { Text("「" + rule.ifText + "」及其全部打卡记录会被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRule(rule) { msg ->
                        ruleToDelete = null
                        onRecord(msg)
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { ruleToDelete = null }) { Text("取消") } }
        )
    }
}

/** 当天已结清的一次性预案：复用同一个白卡完成态（勾选 + 划线 + 灰信息），和长期一致。点进去可重新挂起 */
@Composable
private fun ResolvedOneOffRow(rule: Rule, today: TodayStatus?, onClick: () -> Unit) {
    val status = today ?: when (rule.status) {
        RuleStatus.DONE.name -> TodayStatus(TodayState.DONE)
        RuleStatus.MISSED.name -> TodayStatus(TodayState.MISSED)
        else -> TodayStatus(TodayState.PENDING)
    }
    SwipeRuleCard(
        rule = rule,
        stat = RuleStat(),
        revealedId = null,
        onReveal = {},
        swipeEnabled = false,
        today = status,
        onDone = {},
        onMiss = {},
        onMove = {},
        onArchive = {},
        onDelete = {},
        onOpen = onClick
    )
}
