package com.ifthen.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ifthen.app.data.Folder
import com.ifthen.app.data.Rule
import com.ifthen.app.ui.AppUiState
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.ui.RuleStat
import com.ifthen.app.ui.SuccessChime
import com.ifthen.app.ui.components.ChainCompleteBar
import com.ifthen.app.ui.components.ChainRail
import com.ifthen.app.ui.components.ChainViewPanel
import com.ifthen.app.ui.components.FolderEditorDialog
import com.ifthen.app.ui.components.FolderRow
import com.ifthen.app.ui.components.NextStepBar
import com.ifthen.app.ui.components.RuleChip
import com.ifthen.app.ui.components.SectionHeader
import com.ifthen.app.ui.components.SortPad
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
    var sorting by remember { mutableStateOf(false) }
    var chainOpen by remember { mutableStateOf<Long?>(null) }
    val celebrate by vm.celebrate.collectAsState()

    val inFolder = state.currentFolderId != null
    val groups = state.chain.groups
    val nextStep = state.chain.nextStep

    fun folderLabel(id: Long?): String =
        state.folders.firstOrNull { it.id == id }?.let { "${it.emoji}${it.name}" } ?: "根目录"

    // 系统返回键：排序模式 > 收起展开的动作钮 > 退回上一层文件夹 > 交给系统
    androidx.activity.compose.BackHandler(enabled = sorting || revealedId != null || inFolder) {
        when {
            sorting -> sorting = false
            revealedId != null -> revealedId = null
            else -> vm.openFolder(state.crumbs.lastOrNull()?.parentId)
        }
    }

    // 滚动列表时，把展开的卡片归位
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) revealedId = null
        }
    }

    @Composable
    fun RuleCard(
        rule: Rule,
        step: Int?,
        stepExpanded: Boolean,
        onStepClick: (() -> Unit)?,
        indented: Boolean,
        sortLabel: String?,
        movable: Boolean,
        canUp: Boolean,
        canDown: Boolean,
        onMoveStep: (Int) -> Unit,
        onMoveEdge: (Boolean) -> Unit
    ) {
        val card: @Composable () -> Unit = {
            SwipeRuleCard(
                rule = rule,
                stat = state.stats[rule.id] ?: RuleStat(),
                revealedId = revealedId,
                onReveal = { revealedId = it },
                step = step,
                stepExpanded = stepExpanded,
                onStepClick = onStepClick,
                swipeEnabled = !sorting,
                modifier = if (indented) Modifier.padding(start = 18.dp) else Modifier,
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
        if (sortLabel == null) {
            card()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { card() }
                SortPad(
                    label = sortLabel,
                    movable = movable,
                    canUp = canUp,
                    canDown = canDown,
                    onMove = onMoveStep,
                    onEdge = onMoveEdge,
                    onBlocked = { onRecord("链条成员跟着头部移动；要改链内顺序，去编辑页改接法") }
                )
            }
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
                val cur = state.crumbs.lastOrNull()
                TitleBar(
                    title = cur?.name ?: "果然",
                    emoji = cur?.emoji,
                    onBack = if (inFolder && !sorting) {
                        { vm.openFolder(cur?.parentId) }
                    } else null,
                    action = {
                        if (sorting) {
                            TextButton(onClick = { sorting = false }) { Text("完成") }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { creatingFolder = true }) { Text("+ 文件夹") }
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
                    }
                )
            }

            if (!sorting) {
                val done = celebrate
                if (done != null) {
                    item(key = "celebrate") {
                        Box(Modifier.animateItem()) {
                            ChainCompleteBar(state.chain.todayByHead[done]?.size ?: 0)
                        }
                    }
                } else if (nextStep != null) {
                    val step = nextStep
                    item(key = "next") {
                        Box(Modifier.animateItem()) {
                            NextStepBar(
                                step = step,
                                onCheckIn = {
                                    SuccessChime.play()
                                    vm.logDone(step.rule.id)
                                    onRecord(step.rule.thenText + " ✓")
                                },
                                onSkip = {
                                    vm.logNotDone(step.rule.id)
                                    onRecord("记下了：第 ${step.step} 步跳过")
                                }
                            )
                        }
                    }
                }
            }

            val frequentRules = state.frequentRuleIds.mapNotNull { state.ruleById[it] }
            if (!sorting && !inFolder && frequentRules.isNotEmpty()) {
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
                val folders = state.visibleFolders
                folders.forEachIndexed { i, folder ->
                    item(key = "d" + folder.id) {
                        val row: @Composable () -> Unit = {
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
                        if (!sorting) {
                            row()
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { row() }
                                SortPad(
                                    label = "${i + 1}",
                                    movable = true,
                                    canUp = i > 0,
                                    canDown = i < folders.lastIndex,
                                    onMove = { vm.moveFolderStep(folder.id, it) },
                                    onEdge = { top -> vm.moveFolderToEdge(folder.id, top) },
                                    onBlocked = {}
                                )
                            }
                        }
                    }
                    if (!sorting && folder.id in expandedFolders) {
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
                                    Box(Modifier.animateItem()) {
                                        RuleCard(
                                            rule = child,
                                            step = state.chain.chainPos[child.id]?.step,
                                            stepExpanded = false,
                                            onStepClick = null,
                                            indented = true,
                                            sortLabel = null,
                                            movable = false,
                                            canUp = false,
                                            canDown = false,
                                            onMoveStep = {},
                                            onMoveEdge = {}
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (groups.isNotEmpty()) {
                item { SectionHeader(if (sorting) "预案（长按箭头可连续移动）" else "预案") }
                groups.forEachIndexed { gi, group ->
                    val first = group.rules.first()
                    val headId = state.chain.chainPos[first.id]?.headId ?: first.id
                    val expanded = chainOpen == headId && group.rules.size > 1
                    item(key = "g" + first.id) {
                        Box(Modifier.animateItem()) {
                            if (group.rules.size == 1 && group.offFolderTails.isEmpty()) {
                                val pos = state.chain.chainPos[first.id]
                                RuleCard(
                                    rule = first,
                                    step = pos?.step,
                                    stepExpanded = false,
                                    onStepClick = if (pos != null && pos.step > 1) {
                                        {
                                            val pred = state.activeRules.firstOrNull { it.nextRuleId == first.id }
                                            if (pred != null) {
                                                onRecord("上一步「${pred.ifText}」在 ${folderLabel(pred.folderId)}")
                                            }
                                        }
                                    } else null,
                                    indented = false,
                                    sortLabel = if (sorting) "${gi + 1}" else null,
                                    movable = true,
                                    canUp = gi > 0,
                                    canDown = gi < groups.lastIndex,
                                    onMoveStep = { vm.moveRuleStep(first.id, it) },
                                    onMoveEdge = { top -> vm.moveRuleToEdge(first.id, top) }
                                )
                            } else {
                                ChainRail {
                                    group.rules.forEachIndexed { si, r ->
                                        val pos = state.chain.chainPos[r.id]
                                        RuleCard(
                                            rule = r,
                                            step = pos?.step ?: (si + 1),
                                            stepExpanded = expanded,
                                            onStepClick = { chainOpen = if (expanded) null else headId },
                                            indented = false,
                                            sortLabel = if (sorting) {
                                                if (si == 0) "${gi + 1}" else ""
                                            } else null,
                                            movable = si == 0,
                                            canUp = gi > 0,
                                            canDown = gi < groups.lastIndex,
                                            onMoveStep = { vm.moveRuleStep(first.id, it) },
                                            onMoveEdge = { top -> vm.moveRuleToEdge(first.id, top) }
                                        )
                                    }
                                    group.offFolderTails.forEach { tail ->
                                        Text(
                                            "↳ 在 ${folderLabel(tail.folderId)} ${tail.ifText}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .padding(start = 4.dp, bottom = 2.dp)
                                                .clickable { tail.folderId?.let { vm.openFolder(it) } }
                                        )
                                    }
                                    AnimatedVisibility(visible = expanded) {
                                        ChainViewPanel(
                                            path = group.rules,
                                            offFolder = group.offFolderTails,
                                            folderLabel = { folderLabel(it) },
                                            onUnlink = { id ->
                                                vm.unlinkAt(id) { msg -> msg?.let(onRecord) }
                                            },
                                            onEdit = { onEdit(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
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
            text = { Text("「" + rule.ifText + "」及其全部打卡记录会被永久删除，无法恢复。链条里的下一步会自动接上。") },
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
