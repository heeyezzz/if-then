package com.ifthen.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ifthen.app.data.ChainStatus
import com.ifthen.app.data.Folder
import com.ifthen.app.data.Rule
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import com.ifthen.app.ui.components.InsertLineColor
import com.ifthen.app.ui.AppUiState
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.ui.ChainState
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
    onEditChain: (Long) -> Unit,
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
    val expandedChains by vm.expandedChains.collectAsState()
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var archivedOpen by remember { mutableStateOf(false) }
    var deletingChain by remember { mutableStateOf<Long?>(null) }
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
                today = state.todayStatusByRule[rule.id],
                modifier = if (indented) Modifier.padding(start = 18.dp) else Modifier,
                onDone = {
                    SuccessChime.play()
                    vm.logDone(rule.id)
                    onRecord(rule.thenText + " ✓")
                },
                onMiniDone = {
                    SuccessChime.play()
                    vm.logDone(rule.id, mini = true)
                    onRecord("最小版也算做了 · " + rule.miniThen)
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
                    title = cur?.name ?: "布谷",
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
                                today = state.todayStatusByRule[rule.id],
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
                            ChainState.groupsOf(children).forEach { g ->
                                val head = g.rules.first()
                                item(key = "n" + head.id) {
                                    Box(Modifier.animateItem()) {
                                        if (g.rules.size == 1) {
                                            RuleCard(
                                                rule = head,
                                                step = state.chain.chainPos[head.id]?.step,
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
                                        } else {
                                            // 链在这层当一个条目显示，不铺内部预案；点它进文件夹看/改
                                            val cid = head.chainId
                                            val chain = cid?.let { state.chainById[it] }
                                            Box(Modifier.padding(start = 18.dp)) {
                                                ChainRow(
                                                    title = chain?.name ?: head.ifText,
                                                    colorArgb = chain?.color,
                                                    size = g.rules.size,
                                                    doneToday = state.chain.todayByHead[head.id]?.doneToday ?: 0,
                                                    expanded = false,
                                                    archived = false,
                                                    showMenu = false,
                                                    sorting = false,
                                                    sortLabel = null,
                                                    canUp = false,
                                                    canDown = false,
                                                    onToggle = { vm.openFolder(folder.id) },
                                                    onMenu = {},
                                                    onEditChain = {},
                                                    onToggleArchive = {},
                                                    onColor = {},
                                                    onDelete = {},
                                                    onMoveStep = {},
                                                    onMoveEdge = {},
                                                    compact = true
                                                )
                                            }
                                        }
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
                    if (group.rules.size == 1 && group.offFolderTails.isEmpty()) {
                        item(key = "g" + first.id) {
                            Box(Modifier.animateItem()) {
                                RuleCard(
                                    rule = first,
                                    step = state.chain.chainPos[first.id]?.step,
                                    stepExpanded = false,
                                    onStepClick = null,
                                    indented = false,
                                    sortLabel = if (sorting) "${gi + 1}" else null,
                                    movable = true,
                                    canUp = gi > 0,
                                    canDown = gi < groups.lastIndex,
                                    onMoveStep = { vm.moveRuleStep(first.id, it) },
                                    onMoveEdge = { top -> vm.moveRuleToEdge(first.id, top) }
                                )
                            }
                        }
                    } else {
                        val cid = first.chainId
                        val chain = cid?.let { state.chainById[it] }
                        val expanded = cid != null && cid in expandedChains
                        val doneToday = state.chain.todayByHead[first.id]?.doneToday ?: 0
                        item(key = "g" + first.id) {
                            Box(Modifier.animateItem()) {
                                ChainRow(
                                    title = chain?.name ?: first.ifText,
                                    colorArgb = chain?.color,
                                    size = group.rules.size,
                                    doneToday = doneToday,
                                    expanded = expanded,
                                    archived = false,
                                    showMenu = menuFor == cid,
                                    sorting = sorting,
                                    sortLabel = if (sorting) "${gi + 1}" else null,
                                    canUp = gi > 0,
                                    canDown = gi < groups.lastIndex,
                                    onToggle = { if (cid != null) vm.toggleChainExpanded(cid, !expanded) },
                                    onMenu = { menuFor = if (menuFor == cid) null else cid },
                                    onEditChain = { menuFor = null; onEditChain(first.id) },
                                    onToggleArchive = { if (cid != null) { vm.archiveChain(cid); onRecord("已归档整条链") }; menuFor = null },
                                    onColor = { c -> if (cid != null) vm.setChainColor(cid, c); menuFor = null },
                                    onDelete = { menuFor = null; deletingChain = cid },
                                    onMoveStep = { vm.moveRuleStep(first.id, it) },
                                    onMoveEdge = { top -> vm.moveRuleToEdge(first.id, top) }
                                )
                            }
                        }
                        if (expanded && !sorting) {
                            items(group.rules, key = { "gm" + it.id }) { r ->
                                val pos = state.chain.chainPos[r.id]
                                Box(Modifier.animateItem()) {
                                    RuleCard(
                                        rule = r,
                                        step = pos?.step ?: 1,
                                        stepExpanded = true,
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
            val archivedChains = state.chains.filter { it.status == ChainStatus.ARCHIVED.name }
            if (archivedChains.isNotEmpty() && !sorting) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { archivedOpen = !archivedOpen }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (archivedOpen) "收起已归档" else "展开已归档",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp).rotate(if (archivedOpen) 90f else 0f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "已归档 ${archivedChains.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (archivedOpen) {
                    archivedChains.forEach { ch ->
                        val memCount = state.archivedRules.count { it.chainId == ch.id }
                        item(key = "arc" + ch.id) {
                            Box(Modifier.animateItem()) {
                                ChainRow(
                                    title = ch.name,
                                    colorArgb = ch.color,
                                    size = memCount,
                                    doneToday = 0,
                                    expanded = false,
                                    archived = true,
                                    showMenu = menuFor == ch.id,
                                    sorting = false,
                                    sortLabel = null,
                                    canUp = false,
                                    canDown = false,
                                    onToggle = {},
                                    onMenu = { menuFor = if (menuFor == ch.id) null else ch.id },
                                    onEditChain = {},
                                    onToggleArchive = { vm.unarchiveChain(ch.id); onRecord("已取消归档") },
                                    onColor = { c -> vm.setChainColor(ch.id, c) },
                                    onDelete = { deletingChain = ch.id },
                                    onMoveStep = {},
                                    onMoveEdge = {}
                                )
                            }
                        }
                    }
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

    deletingChain?.let { cid ->
        AlertDialog(
            onDismissRequest = { deletingChain = null },
            title = { Text("删除整条链？") },
            text = { Text("只拆链：成员预案和打卡都留着，退回文件夹。\n连预案一起删：这些预案及其打卡记录永久删除，无法恢复。") },
            confirmButton = {
                Column {
                    TextButton(onClick = { vm.deleteChain(cid, false) { onRecord(it) }; deletingChain = null }) { Text("只拆链") }
                    TextButton(onClick = { vm.deleteChain(cid, true) { onRecord(it) }; deletingChain = null }) { Text("连预案一起删") }
                }
            },
            dismissButton = { TextButton(onClick = { deletingChain = null }) { Text("取消") } }
        )
    }
}

/** 折叠态的链摘要行：与 FolderRow 同一套卡片语言（14dp 圆角、按链色 tinted 容器、40dp 圆形箭头盒） */
@Composable
private fun ChainRow(
    title: String,
    colorArgb: Int?,
    size: Int,
    doneToday: Int,
    expanded: Boolean,
    archived: Boolean,
    showMenu: Boolean,
    sorting: Boolean,
    sortLabel: String?,
    canUp: Boolean,
    canDown: Boolean,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
    onEditChain: () -> Unit,
    onToggleArchive: () -> Unit,
    onColor: (Int?) -> Unit,
    onDelete: () -> Unit,
    onMoveStep: (Int) -> Unit,
    onMoveEdge: (Boolean) -> Unit,
    compact: Boolean = false
) {
    val accent = colorArgb?.let { Color(it) } ?: InsertLineColor
    val container = if (archived) MaterialTheme.colorScheme.surfaceVariant else accent.copy(alpha = 0.10f)
    val palette = listOf<Int?>(null, 0xFFEF5350.toInt(), 0xFFFFB300.toInt(), 0xFF66BB6A.toInt(), 0xFF42A5F5.toInt(), 0xFFAB47BC.toInt())
    val card: @Composable () -> Unit = {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = container)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            if (archived) "$size 步 · 已归档" else "$size 步 · 今日 $doneToday/$size",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!sorting) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .clickable(enabled = !archived) { onToggle() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = when {
                                    compact -> "进入文件夹"
                                    expanded -> "收起整条链"
                                    else -> "展开整条链"
                                },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp).rotate(if (expanded) 90f else 0f)
                            )
                        }
                        if (!compact) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).clickable { onMenu() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "链操作",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (showMenu && !sorting) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!archived) TextButton(onClick = onEditChain) { Text("编辑整串") }
                        TextButton(onClick = onToggleArchive) { Text(if (archived) "取消归档" else "归档") }
                        palette.forEachIndexed { di, c ->
                            Box(
                                Modifier.size(20.dp).padding(2.dp).clip(CircleShape)
                                    .background(c?.let { Color(it) } ?: InsertLineColor.copy(alpha = 0.3f))
                                    .semantics { contentDescription = "链色$di" }
                                    .clickable { onColor(c) }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDelete) { Text("删除整串", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (sorting) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { card() }
            SortPad(
                label = sortLabel ?: "",
                movable = true,
                canUp = canUp,
                canDown = canDown,
                onMove = onMoveStep,
                onEdge = onMoveEdge,
                onBlocked = {}
            )
        }
    } else card()
}
