package com.ifthen.app.ui.screens

import android.content.ClipData
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ifthen.app.data.Folder
import com.ifthen.app.ui.AppUiState
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.ui.components.Breadcrumbs
import com.ifthen.app.ui.components.FolderCard
import com.ifthen.app.ui.components.FolderEditorDialog
import com.ifthen.app.ui.components.RuleCard
import com.ifthen.app.ui.components.SectionHeader
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun ruleDragSource(ruleId: Long) =
    androidx.compose.ui.Modifier.dragAndDropSource {
        androidx.compose.ui.draganddrop.DragAndDropTransferData(
            ClipData.newPlainText("ifthen-rule", ruleId.toString())
        )
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
    var creatingFolder by remember { mutableStateOf(false) }
    var managingFolder by remember { mutableStateOf<Folder?>(null) }
    var pendingDelete by remember { mutableStateOf<Folder?>(null) }
    var deleteImpact by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("预案", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "点卡片 = 做到了 · 长按 = 没做到 · 拖把手 = 换文件夹",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.crumbs.isNotEmpty()) {
                        Breadcrumbs(
                            crumbs = state.crumbs,
                            onRoot = { vm.openFolder(null) },
                            onCrumb = { vm.openFolder(it) }
                        )
                    }
                }
            }
            if (state.frequentRuleIds.isNotEmpty()) {
                item {
                    SectionHeader(title = "今天的现场", subtitle = "最近最常触发的预案，一步可达")
                }
                items(state.frequentRuleIds, key = { "f${it}" }) { id ->
                    val rule = state.ruleById[id] ?: return@items
                    RuleCard(
                        rule = rule,
                        stat = state.stats[id] ?: com.ifthen.app.ui.RuleStat(),
                        emphasized = true,
                        dragSource = ruleDragSource(rule.id),
                        onClick = {
                            com.ifthen.app.ui.SuccessChime.play()
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
            if (state.visibleFolders.isNotEmpty() || state.visibleRules.isEmpty()) {
                item {
                    SectionHeader(
                        title = if (state.currentFolderId == null) "文件夹" else "子文件夹",
                        subtitle = "点进去看 · 长按改名或删除"
                    )
                }
                items(state.visibleFolders, key = { "d${it.id}" }) { folder ->
                    FolderCard(
                        folder = folder,
                        ruleCount = state.activeRules.count { it.folderId == folder.id },
                        onOpen = { vm.openFolder(folder.id) },
                        onManage = { managingFolder = folder },
                        onDropped = { ruleId ->
                            vm.moveRule(ruleId, folder.id)
                            onRecord("已挪进 ${folder.emoji}${folder.name}")
                        }
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = { creatingFolder = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("＋ 新建文件夹") }
            }
            if (state.visibleRules.isEmpty() && state.visibleFolders.isEmpty()) {
                item {
                    Text(
                        "这里还空着。点右下角 + 写第一条预案，或让 AI 帮你想几条。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    )
                }
            } else {
                item { SectionHeader(title = "本层预案") }
                items(state.visibleRules, key = { "a${it.id}" }) { rule ->
                    RuleCard(
                        rule = rule,
                        stat = state.stats[rule.id] ?: com.ifthen.app.ui.RuleStat(),
                        dragSource = ruleDragSource(rule.id),
                        onClick = {
                            com.ifthen.app.ui.SuccessChime.play()
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
            item { Spacer(Modifier.padding(bottom = 80.dp)) }
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
