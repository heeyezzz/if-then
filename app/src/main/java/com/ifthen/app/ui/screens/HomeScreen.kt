package com.ifthen.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ifthen.app.ui.AppUiState
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.ui.components.RuleCard
import com.ifthen.app.ui.components.SectionHeader

@Composable
fun HomeScreen(
    vm: AppViewModel,
    state: AppUiState,
    onEdit: (Long) -> Unit,
    onRecord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("预案", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "点卡片 = 这次做到了 · 长按 = 没做到",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.activeRules.isEmpty()) {
                item {
                    Text(
                        "还没有预案。点右下角 + 写第一条，或让 AI 帮你想几条。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                    )
                }
            }
            if (state.frequentRuleIds.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "今天的现场",
                        subtitle = "最近最常触发的预案，一步可达"
                    )
                }
                items(state.frequentRuleIds, key = { "f${it}" }) { id ->
                    val rule = state.ruleById[id] ?: return@items
                    RuleCard(
                        rule = rule,
                        stat = state.stats[id] ?: com.ifthen.app.ui.RuleStat(),
                        emphasized = true,
                        onClick = {
                            vm.logDone(rule.id)
                            onRecord("已记录：${rule.thenText} ✓")
                        },
                        onLongClick = {
                            vm.logNotDone(rule.id)
                            onRecord("诚实记下了：这次没做")
                        },
                        onEdit = { onEdit(rule.id) }
                    )
                }
            }
            if (state.activeRules.isNotEmpty()) {
                item { SectionHeader(title = "全部预案") }
                items(state.activeRules, key = { "a${it.id}" }) { rule ->
                    RuleCard(
                        rule = rule,
                        stat = state.stats[rule.id] ?: com.ifthen.app.ui.RuleStat(),
                        onClick = {
                            vm.logDone(rule.id)
                            onRecord("已记录：${rule.thenText} ✓")
                        },
                        onLongClick = {
                            vm.logNotDone(rule.id)
                            onRecord("诚实记下了：这次没做")
                        },
                        onEdit = { onEdit(rule.id) }
                    )
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 80.dp)) }
        }
    }
}
