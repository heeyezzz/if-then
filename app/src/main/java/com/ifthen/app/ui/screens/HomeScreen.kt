package com.ifthen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import com.ifthen.app.ui.components.StreakChip
import com.ifthen.app.ui.components.SwipeRuleCard
import com.ifthen.app.ui.components.TitleBar
import kotlinx.coroutines.launch

private class DragState {
    var ruleId by mutableStateOf<Long?>(null)
    var ruleText by mutableStateOf("")
    var ruleThen by mutableStateOf("")
    var streak by mutableStateOf(0)
    var pos by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
    val folderBounds = mutableStateMapOf<Long, Rect>()
    var rootBounds by mutableStateOf<Rect?>(null)
    var previewGroup by mutableStateOf<Long?>(null)
    var previewOrder by mutableStateOf<List<Long>?>(null)

    val rootHovered: Boolean
        get() = ruleId != null && rootBounds?.contains(pos) == true

    val hoverFolderId: Long?
        get() = if (ruleId == null) null
        else folderBounds.entries.firstOrNull { it.value.contains(pos) }?.key
}

private fun orderFor(levelFolderId: Long?, natural: List<Rule>, drag: DragState): List<Rule> {
    val order = drag.previewOrder ?: return natural
    if (drag.previewGroup != levelFolderId) return natural
    return order.mapNotNull { id -> natural.firstOrNull { it.id == id } }
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
    val cardRects = remember { mutableStateMapOf<Long, Rect>() }
    var revealedId by remember { mutableStateOf<Long?>(null) }
    var expandedFolders by remember { mutableStateOf(setOf<Long>()) }
    var creatingFolder by remember { mutableStateOf(false) }
    var managingFolder by remember { mutableStateOf<Folder?>(null) }
    var pendingDelete by remember { mutableStateOf<Folder?>(null) }
    var deleteImpact by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var ruleToDelete by remember { mutableStateOf<Rule?>(null) }

    val inFolder = state.currentFolderId != null
    val lineColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    @Composable
    fun RuleRow(
        rule: Rule,
        indented: Boolean,
        itemModifier: Modifier = Modifier,
        isDragged: Boolean = false
    ) {
        val handlePos = remember { mutableStateOf(Offset.Zero) }
        val lineHeight = with(density) { 5.dp.toPx() }
        val slotModifier = itemModifier
            .alpha(if (isDragged) 0f else 1f)
            .drawBehind {
                if (isDragged) {
                    drawRect(
                        color = lineColor,
                        topLeft = Offset(0f, 0f),
                        size = androidx.compose.ui.geometry.Size(size.width, lineHeight)
                    )
                }
            }
        SwipeRuleCard(
            rule = rule,
            stat = state.stats[rule.id] ?: RuleStat(),
            revealedId = revealedId,
            onReveal = { revealedId = it },
            dragging = drag.ruleId == rule.id,
            swipeEnabled = drag.ruleId == null,
            modifier = if (indented) Modifier.padding(start = 18.dp) else Modifier,
            cardModifier = slotModifier
                .onGloballyPositioned { coords -> cardRects[rule.id] = coords.boundsInWindow() },
            dragHandleModifier = Modifier
                .onGloballyPositioned { handlePos.value = it.positionInWindow() }
                .pointerInput(rule.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { off ->
                            val cardRect = cardRects[rule.id]
                            val finger = handlePos.value + off
                            drag.ruleId = rule.id
                            drag.previewGroup = rule.folderId
                            drag.previewOrder = null
                            drag.ruleText = rule.ifText
                            drag.ruleThen = rule.thenText
                            drag.streak = state.stats[rule.id]?.streak ?: 0
                            drag.pos = finger
                            drag.size = IntSize(
                                cardRect?.width?.toInt() ?: 0,
                                cardRect?.height?.toInt() ?: 0
                            )
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            drag.pos += amount
                            val dragged = drag.ruleId
                            if (dragged != null) {
                                val others = state.activeRules
                                    .filter { it.folderId == drag.previewGroup && it.id != dragged }
                                val idx = others.count { r ->
                                    (cardRects[r.id]?.center?.y ?: 0f) < drag.pos.y
                                }
                                val ids = others.map { it.id }.toMutableList()
                                ids.add(idx.coerceIn(0, ids.size), dragged)
                                drag.previewOrder = ids
                            }
                        },
                        onDragEnd = {
                            val toRoot = drag.rootHovered
                            val target = drag.hoverFolderId
                            val id = drag.ruleId
                            val order = drag.previewOrder
                            drag.ruleId = null
                            drag.previewOrder = null
                            drag.previewGroup = null
                            if (id != null) {
                                when {
                                    toRoot -> {
                                        vm.moveRule(id, null)
                                        onRecord("已移出到根目录")
                                    }
                                    target != null -> {
                                        vm.moveRule(id, target)
                                        val f = state.folders.firstOrNull { it.id == target }
                                        onRecord("已挪进 ${f?.emoji ?: ""}${f?.name ?: ""}")
                                    }
                                    order != null -> vm.reorderLevel(order)
                                }
                            }
                        },
                        onDragCancel = {
                            drag.ruleId = null
                            drag.previewOrder = null
                            drag.previewGroup = null
                        }
                    )
                },
            onDone = {
                SuccessChime.play()
                vm.logDone(rule.id)
                onRecord("${rule.thenText} ✓")
            },
            onMiss = {
                vm.logNotDone(rule.id)
                onRecord("记下了：这次没做")
            },
            onArchive = {
                vm.setArchived(rule, true)
                onRecord("已归档")
            },
            onDelete = { ruleToDelete = rule },
            onOpen = { onEdit(rule.id) }
        )
    }

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

            if (inFolder && drag.ruleId != null) {
                item(key = "rootdrop") {
                    val hovered = drag.rootHovered
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (hovered) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .onGloballyPositioned { drag.rootBounds = it.boundsInWindow() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "松手移出到根目录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hovered) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                                onLongClick = { onEdit(rule.id) }
                            )
                        }
                    }
                }
            }

            if (state.visibleFolders.isNotEmpty()) {
                item { SectionHeader(title = if (inFolder) "子文件夹" else "文件夹") }
                state.visibleFolders.forEach { folder ->
                    item(key = "d${folder.id}") {
                        FolderRow(
                            folder = folder,
                            ruleCount = state.activeRules.count { it.folderId == folder.id },
                            hovered = drag.hoverFolderId == folder.id,
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
                            modifier = Modifier
                                .animateItem()
                                .onGloballyPositioned { coords ->
                                    drag.folderBounds[folder.id] = coords.boundsInWindow()
                                }
                        )
                    }
                    if (folder.id in expandedFolders) {
                        val children = state.activeRules.filter { it.folderId == folder.id }
                        if (children.isEmpty()) {
                            item(key = "empty${folder.id}") {
                                Text(
                                    "这个文件夹是空的",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 26.dp, bottom = 6.dp)
                                )
                            }
                        } else {
                            val orderedChildren = orderFor(folder.id, children, drag)
                            orderedChildren.forEach { child ->
                                item(key = "n${child.id}") {
                                    RuleRow(
                                        child,
                                        indented = true,
                                        itemModifier = Modifier.animateItem(),
                                        isDragged = drag.previewOrder != null && drag.ruleId == child.id
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val rules = state.visibleRules
            if (rules.isNotEmpty()) {
                item { SectionHeader("预案") }
                val ordered = orderFor(state.currentFolderId, rules, drag)
                items(ordered, key = { "a${it.id}" }) { rule ->
                    RuleRow(
                        rule,
                        indented = false,
                        itemModifier = Modifier.animateItem(),
                        isDragged = drag.previewOrder != null && drag.ruleId == rule.id
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

        // 拖拽跟手浮层：整卡矩形，且始终留在屏幕内
        drag.ruleId?.let {
            val ghostWidthDp = with(density) { drag.size.width.toDp() }
            val ghostWidthPx = drag.size.width.toFloat()
            val ghostHeightPx = drag.size.height.toFloat()
            Card(
                modifier = Modifier
                    .width(ghostWidthDp)
                    .offset {
                        val maxX = (screenWidthPx - ghostWidthPx).coerceAtLeast(0f)
                        IntOffset(
                            (drag.pos.x - ghostWidthPx / 2f).coerceIn(0f, maxX).toInt(),
                            (drag.pos.y - ghostHeightPx).toInt()
                        )
                    }
                    .scale(1.03f)
                    .alpha(0.97f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = drag.ruleText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "→ ${drag.ruleThen}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    StreakChip(drag.streak)
                }
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

    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("删除这条预案？") },
            text = { Text("「${rule.ifText}」及其全部打卡记录会被永久删除，无法恢复。") },
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