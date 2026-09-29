package com.ifthen.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ifthen.app.data.Folder
import com.ifthen.app.data.Rule
import com.ifthen.app.ui.RuleStat
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

val FolderEmojis = listOf("📁", "🌙", "☀️", "💼", "🏋️", "📚", "🍔", "🧘", "✈️", "🌱")
val FolderColors = listOf(0xFF1F6E5C, 0xFF2F5DA8, 0xFF6A4FA3, 0xFFB8860B, 0xFF8C4A2F, 0xFF0F7B7B)

val ActionMiss = Color(0xFFE08A2E)
val ActionArchive = Color(0xFF5B6E8C)
val ActionDelete = Color(0xFFC0392B)

@Composable
fun TitleBar(
    title: String,
    emoji: String? = null,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Text(
                "‹",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
        AnimatedContent(
            targetState = if (emoji == null) title else "$emoji $title",
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 })
                    .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 6 })
            },
            label = "title",
            modifier = Modifier.weight(1f)
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        action?.invoke()
    }
}

@Composable
fun SectionHeader(
    title: String,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

/** 连击数：数字变化时向上滚动切换 */
@Composable
fun StreakChip(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(Modifier.width(3.dp))
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                (slideInVertically(tween(200)) { it } + fadeIn(tween(200)))
                    .togetherWith(slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)))
            },
            label = "streak"
        ) { n ->
            Text(
                text = "$n",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/** 高频药丸：点=完成，长按=进编辑页 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RuleChip(
    rule: Rule,
    streak: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chipScale"
    )
    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .combinedClickable(
                onClick = {
                    pressed = true
                    onClick()
                },
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LaunchedEffect(pressed) {
            if (pressed) {
                kotlinx.coroutines.delay(220)
                pressed = false
            }
        }
        Text(
            rule.ifText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        if (streak > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                "🔥$streak",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 卡片手势：右滑=完成，左滑=露出动作钮，点=进编辑页，把手长按=拖拽换夹。
 * 完成不删除卡片（预案是长期的），所以右滑到底后卡片回弹并给出成功反馈。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeRuleCard(
    rule: Rule,
    stat: RuleStat,
    revealedId: Long?,
    onReveal: (Long?) -> Unit,
    dragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    onDone: () -> Unit,
    onMiss: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    cardModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val actionsWidthPx = with(density) { 196.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    var pendingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var cardWidthPx by remember { mutableStateOf(0f) }
    var succeeded by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (succeeded) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cardPop"
    )
    val revealed = revealedId == rule.id
    val sourceAlpha by animateFloatAsState(
        targetValue = if (dragging) 0.35f else 1f,
        animationSpec = tween(150),
        label = "sourceAlpha"
    )

    LaunchedEffect(revealed) {
        if (!revealed && offsetX.value != 0f) {
            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // 左滑露出的动作钮（在卡片下层）
        val buttonsVisible by animateFloatAsState(
            targetValue = if (!dragging && offsetX.value < -4f) 1f else 0f,
            animationSpec = tween(120),
            label = "buttonsAlpha"
        )
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .alpha(buttonsVisible)
                .height(with(density) { 56.dp })
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RoundAction("没做", ActionMiss, Icons.Filled.Close) {
                onMiss(); scope.launch { offsetX.animateTo(0f, spring()) }; onReveal(null)
            }
            RoundAction("归档", ActionArchive, Icons.Filled.Archive) {
                onArchive(); scope.launch { offsetX.animateTo(0f, spring()) }; onReveal(null)
            }
            RoundAction("删除", ActionDelete, Icons.Filled.DeleteOutline) {
                onDelete(); scope.launch { offsetX.animateTo(0f, spring()) }; onReveal(null)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(cardModifier)
                .onSizeChanged { cardWidthPx = it.width.toFloat() }
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .scale(cardScale)
                .alpha(sourceAlpha)
                .pointerInput(rule.id, revealed) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            pendingJob?.cancel()
                            val target = when {
                                offsetX.value > 140f -> 1f   // 右滑到底 = 完成
                                offsetX.value < -actionsWidthPx / 2 -> -actionsWidthPx
                                else -> 0f
                            }
                            if (target == 1f) {
                                pendingJob = scope.launch {
                                    succeeded = true
                                    onDone()
                                    offsetX.animateTo(
                                        cardWidthPx.coerceAtLeast(320f), tween(180)
                                    )
                                    offsetX.snapTo(0f)
                                    succeeded = false
                                }
                            } else {
                                pendingJob = scope.launch {
                                    offsetX.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                                onReveal(if (target == 0f) null else rule.id)
                            }
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            pendingJob?.cancel()
                            val next = (offsetX.value + delta)
                                .coerceIn(-actionsWidthPx, cardWidthPx.coerceAtLeast(320f))
                            pendingJob = scope.launch { offsetX.snapTo(next) }
                        }
                    )
                },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .combinedClickable(onClick = onOpen, onLongClick = null)
                    .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = rule.ifText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "→ ${rule.thenText}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        StreakChip(stat.streak)
                    }
                }
                Icon(
                    imageVector = Icons.Filled.DragIndicator,
                    contentDescription = "长按拖到文件夹",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(22.dp)
                        .then(dragHandleModifier)
                )
            }
        }
    }
}

@Composable
private fun RoundAction(
    label: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 细行式文件夹条目：点=进入，长按=管理，可作拖拽落点 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderRow(
    folder: Folder,
    ruleCount: Int,
    hovered: Boolean,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(folder.color)
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "folderHover"
    )
    Card(
        modifier = modifier.fillMaxWidth().scale(scale),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hovered) accent.copy(alpha = 0.34f) else accent.copy(alpha = 0.10f)
        ),
        border = if (hovered) BorderStroke(2.dp, accent) else null
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(folder.emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (ruleCount > 0) {
                Text(
                    "$ruleCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
            }
            val arrowRotation by animateFloatAsState(
                targetValue = if (expanded) 90f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "arrow"
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onToggleExpand() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "收起该文件夹" else "在本页展开该文件夹",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(arrowRotation)
                )
            }
        }
    }
}

@Composable
fun FolderEditorDialog(
    initial: Folder?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String, color: Long) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: FolderEmojis.first()) }
    var color by remember { mutableStateOf(initial?.color ?: FolderColors.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建文件夹" else "管理文件夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称") }, singleLine = true
                )
                Text("图标", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FolderEmojis.forEach { e ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (e == emoji) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                e,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.clickable { emoji = e }
                            )
                        }
                    }
                }
                Text("颜色", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FolderColors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (c == color)
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { color = c }
                        )
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除此文件夹（连带内容）", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), emoji, color) }
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun Heatmap(counts: List<Int>, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val low = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val mid = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val high = MaterialTheme.colorScheme.primary
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        counts.chunked(10).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { colIndex, c ->
                    val delayMs = (rowIndex * 10 + colIndex) * 12
                    val alpha by animateFloatAsState(
                        targetValue = if (appeared) 1f else 0f,
                        animationSpec = tween(durationMillis = 260, delayMillis = delayMs),
                        label = "heatAlpha"
                    )
                    val level = when {
                        c <= 0 -> base
                        c <= 2 -> low
                        c <= 5 -> mid
                        else -> high
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(level.copy(alpha = level.alpha * alpha))
                    )
                }
                repeat(10 - row.size) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}