package com.ifthen.app.ui.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ifthen.app.data.Folder
import com.ifthen.app.ui.AppUiState
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.ui.SuccessChime
import com.ifthen.app.ui.components.FolderEditorDialog
import com.ifthen.app.ui.components.FolderRow
import com.ifthen.app.ui.components.RuleCard
import com.ifthen.app.ui.components.RuleChip
import com.ifthen.app.ui.components.SectionHeader
import com.ifthen.app.ui.components.TitleBar
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private class DragState {
    var ruleId by mutableStateOf<Long?>(null)
    var ruleText by mutableStateOf("")
    var pos by mutableStateOf(Offset.Zero)
    val folderBounds = mutableStateMapOf<Long, Rect>()

    val hoverFolderId: Long?
        get() = if (ruleId == null) null
        else folderBounds.entries.firstOrNull { it.value.contains(pos) }?.key
}

@Composable
fun HomeScreen(
    vm: AppViewModel,
    state: AppUiState,
    onEdit: (Long) -> Unit,
    onRecord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val drag = remember { DragState() }
    var creatingFolder by remember { mutableStateOf(false) }
    var managingFolder by remember { mutableStateOf<Folder?>(null) }
    var pendingDelete by remember { mutableStateOf<Folder?>(null) }
    var deleteImpact by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val inFolder = state.currentFolderId != null

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
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
                        items(frequentRules, key = { "f${it.id}" }) { rule ->
                            RuleChip(
                                rule = rule,
                                streak = state.stats[rule.id]?.streak ?: 0,
                                onClick = {
                                    SuccessChime.play()
                                    vm.logDone(rule.id)
                                    onRecord("${rule.thenText} ✓")
                                },
                                onLongClick = {
                                    vm.logNotDone(rule.id)
                                    onRecord("记下了：这次没做")
                                }
                            )
                        }
                    }
                }
            }

            if (state.visibleFolders.isNotEmpty()) {
                item {
                    SectionHeader(title = if (inFolder) "子文件夹" else "文件夹")
                }
                items(state.visibleFolders, key = { "d${it.id}" }) { folder ->
                    FolderRow(
                        folder = folder,
                        ruleCount = state.activeRules.count { it.folderId == folder.id },
                        hovered = drag.hoverFolderId == folder.id,
                        onClick = { vm.openFolder(folder.id) },
                        onLongClick = { managingFolder = folder },
                        modifier = Modifier.onGloballyPositioned { coords ->
                            drag.folderBounds[folder.id] = coords.boundsInWindow()
                        }
                    )
                }
            }

            val rules = state.visibleRules
            if (rules.isNotEmpty()) {
                item { SectionHeader(title = "预案") }
                items(rules, key = { "a${it.id}" }) { rule ->
                    val handlePos = remember { mutableStateOf(Offset.Zero) }
                    RuleCard(
                        rule = rule,
                        stat = state.stats[rule.id] ?: com.ifthen.app.ui.RuleStat(),
                        dragHandleModifier = Modifier
                            .onGloballyPositioned { handlePos.value = it.positionInWindow() }
                            .pointerInput(rule.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { off ->
                                        drag.ruleId = rule.id
                                        drag.ruleText = rule.ifText
                                        drag.pos = handlePos.value + off
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        drag.pos += amount
                                    },
                                    onDragEnd = {
                                        val target = drag.hoverFolderId
                                        val id = drag.ruleId
                                        drag.ruleId = null
                                        if (target != null && id != null) {
                                            vm.moveRule(id, target)
                                            val f = state.folders.firstOrNull { it.id == target }
                                            onRecord("已挪进 ${f?.emoji ?: ""}${f?.name ?: ""}")
                                        }
                                    },
                                    onDragCancel = { drag.ruleId = null }
                                )
                            },
                        onClick = {
                            SuccessChime.play()
                            vm.logDone(rule.id)
                            onRecord("${rule.thenText} ✓")
                        },
                        onLongClick = {
                            vm.logNotDone(rule.id)
                            onRecord("记下了：这次没做")
                        },
                        onEdit = { onEdit(rule.id) }
                    )
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

        // 拖拽跟手浮层
        if (drag.ruleId != null) {
            Card(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (drag.pos.x - 60).roundToInt(),
                            (drag.pos.y - 60).roundToInt()
                        )
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Text(
                    drag.ruleText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
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

    pendingDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${folder.emoji}${folder.name}」？") },
            text = {
                val imp = deleteImpact
                Text(
                    if (imp == null) "计算中…"
                    else "将连带删除 ${imp.first} 条预案及 ${imp.second} 条打卡记录（含所有子文件夹），无法恢复。"
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