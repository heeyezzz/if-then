package com.ifthen.app.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ifthen.app.data.Folder
import com.ifthen.app.data.Rule
import com.ifthen.app.ui.AppUiState
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.ui.RuleStat
import com.ifthen.app.ui.SuccessChime
import com.ifthen.app.ui.components.FolderEditorDialog
import com.ifthen.app.ui.components.FolderRow
import com.ifthen.app.ui.components.RuleChip
import com.ifthen.app.ui.components.SectionHeader
import com.ifthen.app.ui.components.SwipeRuleCard
import com.ifthen.app.ui.components.TitleBar
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    vm: AppViewModel,
    state: AppUiState,
    onEdit: (Long) -> Unit,
    onRecord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var revealedId by remember { mutableStateOf<Long?>(null) }
    var expandedFolders by remember { mutableStateOf(setOf<Long>()) }
    var creatingFolder by remember { mutableStateOf(false) }
    var managingFolder by remember { mutableStateOf<Folder?>(null) }
    var pendingDelete by remember { mutableStateOf<Folder?>(null) }
    var deleteImpact by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var ruleToDelete by remember { mutableStateOf<Rule?>(null) }
    var movingRule by remember { mutableStateOf<Rule?>(null) }

    val inFolder = state.currentFolderId != null

    // 系统返回键：先收起展开的动作钮，再退回上一层文件夹；都在根目录时才交给系统
    androidx.activity.compose.BackHandler(enabled = revealedId != null || inFolder) {
        when {
            revealedId != null -> revealedId = null
            inFolder -> vm.openFolder(state.crumbs.lastOrNull()?.parentId)
        }
    }

    // 滚动列表时，把展开的卡片归位
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) revealedId = null
        }
    }

    @Composable
    fun RuleRow(rule: Rule, indented: Boolean, itemModifier: Modifier = Modifier) {
        SwipeRuleCard(
            rule = rule,
            stat = state.stats[rule.id] ?: RuleStat(),
            revealedId = revealedId,
            onReveal = { revealedId = it },
            modifier = if (indented) Modifier.padding(start = 18.dp) else Modifier,
            cardModifier = itemModifier,
            onDone = {
                SuccessChime.play()
                vm.logDone(rule.id)
                onRecord(rule.thenText + " ✓")
            },
            onMiss = {
                vm.logNotDone(rule.id)
                onRecord("记下了：这次没做")
            },
            onMove = { movingRule = rule },
            onArchive = {
                vm.setArchived(rule, true)
                onRecord("已归档")
            },
            onDelete = { ruleToDelete = rule },
            onOpen = {
                // 有卡片展开时，第一下只负责归位，不跳转
                if (revealedId != null) revealedId = null else onEdit(rule.id)
            }
        )
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
                val cur = state.crumbs.lastOrNull()
                TitleBar(
                    title = cur?.name ?: "预案",
                    emoji = cur?.emoji,
                    onBack = if (inFolder) {
                        { vm.openFolder(cur?.parentId) }
                    } else null,
                    action = {
                        TextButton(onClick = { creatingFolder = true }) { Text("+ 文件夹") }
                    }
                )
            }

            val frequentRules = state.frequentRuleIds.mapNotNull { state.ruleById[it] }
            if (!inFolder && frequentRules.isNotEmpty()) {
                item { SectionHeader("今天的现场") }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(frequentRules, key = { "f" + it.id }) { rule ->
                            RuleChip(
                                rule = rule,
                                streak = state.stats[rule.id]?.streak ?: 0,
                                onClick = {
                                    SuccessChime.play()
                                    vm.logDone(rule.id)
                                    onRecord(rule.thenText + " ✓")
                                },
                                onLongClick = { onEdit(rule.id) }
                            )
                        }
                    }
                }
            }

            if (state.visibleFolders.isNotEmpty()) {
                item { SectionHeader(title = if (inFolder) "子文件夹" else "文件夹") }
                state.visibleFolders.forEach { folder ->
                    item(key = "d" + folder.id) {
                        FolderRow(
                            folder = folder,
                            ruleCount = state.activeRules.count { it.folderId == folder.id },
                            hovered = false,
                            expanded = folder.id in expandedFolders,
                            onToggleExpand = {
                                expandedFolders = if (folder.id in expandedFolders) {
                                    expandedFolders - folder.id
                                } else {
                                    expandedFolders + folder.id
                                }
                            },
                            onClick = { vm.openFolder(folder.id) },
                            onLongClick = { managingFolder = folder },
                            modifier = Modifier.animateItem()
                        )
                    }
                    if (folder.id in expandedFolders) {
                        val children = state.activeRules.filter { it.folderId == folder.id }
                        if (children.isEmpty()) {
                            item(key = "e" + folder.id) {
                                Text(
                                    "这个文件夹是空的",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 26.dp, bottom = 6.dp)
                                )
                            }
                        } else {
                            children.forEach { child ->
                                item(key = "n" + child.id) {
                                    RuleRow(child, indented = true, itemModifier = Modifier.animateItem())
                                }
                            }
                        }
                    }
                }
            }

            val rules = state.visibleRules
            if (rules.isNotEmpty()) {
                item { SectionHeader("预案") }
                items(rules, key = { "a" + it.id }) { rule ->
                    RuleRow(rule, indented = false, itemModifier = Modifier.animateItem())
                }
            } else if (state.visibleFolders.isEmpty() && frequentRules.isEmpty()) {
                item {
                    Text(
                        if (inFolder) "这个文件夹还空着，点右下角 + 添加预案。"
                        else "还没有预案。点右下角 + 新建，或让 AI 帮你想几条。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    // 快捷切换文件夹（左滑「移夹」入口）
    movingRule?.let { rule ->
        val rows = ArrayList<Pair<Int, Folder?>>()
        rows.add(0 to null)
        val childrenOf = state.folders.groupBy { it.parentId }
        fun walk(parentId: Long?, depth: Int) {
            childrenOf[parentId].orEmpty().forEach { f ->
                rows.add(depth to f)
                walk(f.id, depth + 1)
            }
        }
        walk(null, 1)
        AlertDialog(
            onDismissRequest = { movingRule = null },
            title = { Text("把「" + rule.ifText + "」移到…") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    rows.forEach { item ->
                        val depth = item.first
                        val f = item.second
                        val isCurrent = f?.id == rule.folderId
                        TextButton(
                            onClick = {
                                vm.moveRule(rule.id, f?.id)
                                movingRule = null
                                onRecord(if (f == null) "已移到根目录" else "已移到 " + f.emoji + f.name)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(start = (12 + depth * 20).dp)
                        ) {
                            Text(
                                text = (if (f == null) "根目录" else f.emoji + f.name) +
                                    if (isCurrent) "（当前）" else "",
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { movingRule = null }) { Text("取消") } }
        )
    }

    if (creatingFolder) {
        FolderEditorDialog(
            initial = null,
            onDismiss = { creatingFolder = false },
            onConfirm = { name, emoji, color ->
                vm.createFolder(name, emoji, color)
                creatingFolder = false
            }
        )
    }

    managingFolder?.let { folder ->
        FolderEditorDialog(
            initial = folder,
            onDismiss = { managingFolder = null },
            onConfirm = { name, emoji, color ->
                vm.updateFolder(folder.copy(name = name, emoji = emoji, color = color))
                managingFolder = null
            },
            onDelete = {
                managingFolder = null
                pendingDelete = folder
                deleteImpact = null
                scope.launch { deleteImpact = vm.folderDeleteImpact(folder.id) }
            }
        )
    }

    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("删除这条预案？") },
            text = { Text("「" + rule.ifText + "」及其全部打卡记录会被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRule(rule)
                    ruleToDelete = null
                    onRecord("已删除")
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { ruleToDelete = null }) { Text("取消") } }
        )
    }

    pendingDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「" + folder.emoji + folder.name + "」？") },
            text = {
                val imp = deleteImpact
                Text(
                    if (imp == null) "计算中…"
                    else "将连带删除 " + imp.first + " 条预案及 " + imp.second + " 条打卡记录（含所有子文件夹），无法恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFolderCascade(folder.id)
                    pendingDelete = null
                }) { Text("全部删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}
