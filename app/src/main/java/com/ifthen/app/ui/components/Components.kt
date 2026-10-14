package com.ifthen.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileMove
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ifthen.app.Constants
import com.ifthen.app.data.Folder
import com.ifthen.app.data.Rule
import com.ifthen.app.ui.Heat
import com.ifthen.app.ui.NextStep
import com.ifthen.app.ui.RuleStat
import com.ifthen.app.ui.TodayState
import com.ifthen.app.ui.TodayStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

val FolderEmojis = listOf("📁", "🌙", "☀️", "💼", "🏋️", "📚", "🍔", "🧘", "✈️", "🌱")
val FolderColors = listOf(0xFF1F6E5C, 0xFF2F5DA8, 0xFF6A4FA3, 0xFFB8860B, 0xFF8C4A2F, 0xFF0F7B7B)

/** 插入指示线：深绿（与主色一致的品牌色） */
val InsertLineColor = Color(0xFF1F6E5C)

val ActionMiss = Color(0xFFE08A2E)
/** 动作钮列宽与间距：露出宽度由它们算出，避免卡片压住最左侧按钮 */
val ActionColumnWidth = 52
val ActionGap = 6

val ActionMove = Color(0xFF3D6E8C)
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
    today: TodayStatus? = null,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chipScale"
    )
    val chipAlpha by animateFloatAsState(
        targetValue = if (today?.state == TodayState.DONE) 0.55f else 1f,
        animationSpec = tween(180),
        label = "chipDoneAlpha"
    )
    Row(
        modifier = modifier
            .scale(scale)
            .alpha(chipAlpha)
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
        val glyph = when (today?.state) {
            TodayState.DONE -> "✓" to MaterialTheme.colorScheme.primary
            TodayState.MISSED -> "✗" to ActionMiss
            TodayState.PENDING -> "…" to MaterialTheme.colorScheme.onSurfaceVariant
            else -> null
        }
        if (glyph != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                glyph.first,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = glyph.second
            )
        }
    }
}

private val stampTimeFmt = SimpleDateFormat("HH:mm", Locale.CHINA)

/** 卡片右下角的今日状态戳记：做/没做/待确认。补记的条目不写具体时刻 */
@Composable
fun TodayStamp(status: TodayStatus?, modifier: Modifier = Modifier) {
    if (status == null || status.state == TodayState.NONE) return
    val text: String
    val color: Color
    when (status.state) {
        TodayState.DONE -> {
            text = if (status.backfill) "今天 ✓ · 补记"
            else "今天 " + stampTimeFmt.format(Date(status.at)) + " ✓" +
                if (status.mini) " 最小版" else ""
            color = MaterialTheme.colorScheme.primary
        }
        TodayState.MISSED -> {
            text = if (status.backfill) "今天 · 没做（补记）"
            else "今天 " + stampTimeFmt.format(Date(status.at)) + " · 没做"
            color = ActionMiss
        }
        else -> {
            text = "今天 · 待确认"
            color = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
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
    onDone: () -> Unit,
    onMiss: () -> Unit,
    onMove: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    step: Int? = null,
    stepExpanded: Boolean = false,
    onStepClick: (() -> Unit)? = null,
    swipeEnabled: Boolean = true,
    onMiniDone: (() -> Unit)? = null,
    overdueDays: Int? = null,
    dueLabel: String? = null,
    today: TodayStatus? = null,
    moveLabel: String = "移夹",
    moveIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.DriveFileMove,
    cardModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val actionsWidthPx = with(density) { (ActionColumnWidth * 4 + ActionGap * 3 + 2).dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    var pendingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var cardWidthPx by remember { mutableStateOf(0f) }
    var succeeded by remember { mutableStateOf(false) }
    // 四个动作钮静止时被卡片整个盖住，从没被看到却要跟着组合一次；首次左拖才挂上来
    var actionsMounted by remember { mutableStateOf(false) }
    val revealProgress = if (offsetX.value < 0f) {
        (-offsetX.value / actionsWidthPx).coerceIn(0f, 1f)
    } else 0f
    val cardScale by animateFloatAsState(
        targetValue = if (succeeded) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cardPop"
    )
    val revealed = revealedId == rule.id

    val isDone = today?.state == TodayState.DONE
    val contentAlpha by animateFloatAsState(
        targetValue = if (isDone) 0.55f else 1f,
        animationSpec = tween(180),
        label = "doneContentAlpha"
    )
    val cardColor by animateColorAsState(
        targetValue = lerp(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.background,
            if (isDone) 0.6f else 0f
        ),
        animationSpec = tween(180),
        label = "doneCardColor"
    )

    val dragModifier = if (swipeEnabled) {
        Modifier.pointerInput(rule.id, revealed) {
            detectHorizontalDragGestures(
                onDragStart = { if (!actionsMounted) actionsMounted = true },
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
        }
    } else Modifier

    LaunchedEffect(revealed) {
        if (!revealed && offsetX.value != 0f) {
            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // 左滑露出的动作钮（在卡片下层）
        if (actionsMounted) {
            val buttonsVisible by animateFloatAsState(
                targetValue = if (offsetX.value < -4f) 1f else 0f,
                animationSpec = tween(120),
                label = "buttonsAlpha"
            )
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .alpha(buttonsVisible)
                    .height(with(density) { 76.dp })
                    .padding(end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ActionGap.dp)
            ) {
                RoundAction("没做", ActionMiss, Icons.Filled.Close) {
                    onMiss(); scope.launch { offsetX.animateTo(0f, spring()) }; onReveal(null)
                }
                RoundAction(moveLabel, ActionMove, moveIcon) {
                    scope.launch { offsetX.animateTo(0f, spring()) }; onReveal(null); onMove()
                }
                RoundAction("归档", ActionArchive, Icons.Filled.Archive) {
                    onArchive(); scope.launch { offsetX.animateTo(0f, spring()) }; onReveal(null)
                }
                RoundAction("删除", ActionDelete, Icons.Filled.DeleteOutline) {
                    onDelete(); scope.launch { offsetX.animateTo(0f, spring()) }; onReveal(null)
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(cardModifier)
                .onSizeChanged { cardWidthPx = it.width.toFloat() }
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(if (cardScale == 1f) Modifier else Modifier.scale(cardScale))
                .then(dragModifier),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onOpen)
                    .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step != null) {
                    ChainStepBadge(step = step, expanded = stepExpanded, onClick = { onStepClick?.invoke() })
                    Spacer(Modifier.width(10.dp))
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val fade = contentAlpha * (1f - revealProgress)
                    Column(
                        // 值为 1 时不挂 alpha：graphicsLayer 是录制成本，120Hz 下瓶颈就在录制
                        if (fade < 0.999f) Modifier.alpha(fade) else Modifier,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        TaggedLine(
                            tag = "如果",
                            tone = TagTone.Condition,
                            text = rule.ifText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            trailing = overdueDays?.let { days ->
                                {
                                    Text(
                                        "逾期 $days 天",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                        TaggedLine(
                            tag = "就",
                            tone = TagTone.Action,
                            text = rule.thenText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = if (isDone) TextDecoration.LineThrough
                            else TextDecoration.None,
                            trailing = dueLabel?.let { label ->
                                {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } ?: { StreakChip(stat.streak) }
                        )
                        if (rule.miniThen.isNotBlank() && onMiniDone != null) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onMiniDone() }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.width(TagColumnWidth),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        "2min",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    rule.miniThen,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "做这个",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                    if (today != null && today.state != TodayState.NONE) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(if (revealProgress == 0f) Modifier else Modifier.alpha(1f - revealProgress))
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) { TodayStamp(today) }
                    }
                }
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
        modifier = Modifier
            .width(ActionColumnWidth.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
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

/** 热力图：一天一格（「全部」档一周一格），点一格看那天/那周的流水 */
@Composable
fun Heatmap(
    heat: Heat,
    selected: Int?,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 空格子用 onSurfaceVariant 半透明：surfaceVariant 卡片上再铺 surfaceVariant 会直接看不见
    val base = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val low = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val mid = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val high = MaterialTheme.colorScheme.primary
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        heat.cells.chunked(Constants.HEATMAP_CELLS_PER_ROW).forEachIndexed { rowIndex, row ->
            val startIndex = rowIndex * Constants.HEATMAP_CELLS_PER_ROW
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { colIndex, cell ->
                    val index = startIndex + colIndex
                    val delayMs = index * 8
                    val alpha by animateFloatAsState(
                        targetValue = if (appeared) 1f else 0f,
                        animationSpec = tween(durationMillis = 260, delayMillis = delayMs),
                        label = "heatAlpha"
                    )
                    val level = when {
                        cell.count <= 0 -> base
                        cell.count <= 2 -> low
                        cell.count <= 5 -> mid
                        else -> high
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(level.copy(alpha = level.alpha * alpha), RoundedCornerShape(6.dp))
                            .then(
                                if (cell.isToday) Modifier.border(
                                    1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    RoundedCornerShape(6.dp)
                                ) else Modifier
                            )
                            .then(
                                if (selected == index) Modifier.border(
                                    2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)
                                ) else Modifier
                            )
                            .semantics {
                                contentDescription = "${cell.label}，做了 ${cell.count} 次" +
                                    if (cell.isToday) "，今天" else ""
                            }
                            .clickable { onCellClick(index) }
                    )
                }
                repeat(Constants.HEATMAP_CELLS_PER_ROW - row.size) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                heat.cells.firstOrNull()?.label.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (heat.byWeek) "一格 = 一周" else "一格 = 一天",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "框住的是今天",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 「如果 / 就」语义标签的共用列宽：两个标签左边缘对齐，正文左边缘也对齐 */
private val TagColumnWidth = 40.dp

enum class TagTone { Condition, Action }

/** 小标签：条件＝松绿容器色，动作＝琥珀容器色（走 MD3 容器 token，暗色模式自动跟随） */
@Composable
fun SemanticTag(text: String, tone: TagTone, modifier: Modifier = Modifier) {
    val bg: Color
    val fg: Color
    when (tone) {
        TagTone.Condition -> {
            bg = MaterialTheme.colorScheme.primary
            fg = MaterialTheme.colorScheme.onPrimary
        }
        TagTone.Action -> {
            bg = MaterialTheme.colorScheme.secondaryContainer
            fg = MaterialTheme.colorScheme.onSecondaryContainer
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

/** 标签 + 正文一行；正文换行时标签贴第一行 */
@Composable
fun TaggedLine(
    tag: String,
    tone: TagTone,
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    maxLines: Int = 4,
    modifier: Modifier = Modifier,
    textDecoration: TextDecoration = TextDecoration.None,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .width(TagColumnWidth)
                .padding(top = 2.dp),
            contentAlignment = Alignment.CenterStart
        ) { SemanticTag(tag, tone) }
        Text(
            text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textDecoration = textDecoration,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Box(Modifier.padding(start = 8.dp)) { trailing() }
        }
    }
}

/** 链条序号钮：点它展开/收起链条视图 */
@Composable
fun ChainStepBadge(
    step: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (expanded) primary else primary.copy(alpha = 0.14f))
            .semantics { contentDescription = "链条第 $step 步" }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$step",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (expanded) Color.White else primary
        )
    }
}

/** 链条组容器：左侧一条品牌绿竖线把头到尾串起来 */
@Composable
fun ChainRail(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 2.dp.toPx()
                drawLine(
                    color = InsertLineColor.copy(alpha = 0.45f),
                    start = Offset(x, 12.dp.toPx()),
                    end = Offset(x, size.height - 12.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .padding(start = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

/** 顶部「下一步」提示条：链条推进后的单一焦点 */
@Composable
fun NextStepBar(
    step: NextStep,
    onCheckIn: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "▶ 下一步 · 第 ${step.step}/${step.size} 步",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    step.rule.ifText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "接「${step.prevIfText}」之后 → ${step.rule.thenText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onCheckIn) { Text("打卡") }
            TextButton(onClick = onSkip) { Text("跳过") }
        }
    }
}

/** 链条全做齐的一次性反馈条 */
@Composable
fun ChainCompleteBar(size: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎉", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Text(
                "链条完成 · $size 步全做",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
        }
    }
}

/** 就地展开的链条视图：头→尾清单，可在任意节点处断开 */
@Composable
fun ChainViewPanel(
    path: List<Rule>,
    offFolder: List<Rule>,
    folderLabel: (Long?) -> String,
    onUnlink: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
            path.forEachIndexed { i, r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = InsertLineColor,
                        modifier = Modifier.width(16.dp)
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        TaggedLine(
                            tag = "如果",
                            tone = TagTone.Condition,
                            text = r.ifText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        TaggedLine(
                            tag = "就",
                            tone = TagTone.Action,
                            text = r.thenText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (i > 0) {
                        TextButton(onClick = { onUnlink(r.id) }) {
                            Text("断开", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TextButton(onClick = { onEdit(r.id) }) {
                        Text("编辑", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            offFolder.forEach { r ->
                Text(
                    "↳ 后续「${r.ifText}」在 ${folderLabel(r.folderId)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }
        }
    }
}

/** 排序模式的方向控件：⇈ 置顶 ▲ 上移 ▼ 下移 ⇊ 置底，▲▼ 长按连续移动 */
@Composable
fun SortPad(
    label: String,
    movable: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onMove: (Int) -> Unit,
    onEdge: (Boolean) -> Unit,
    onBlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    fun dim(up: Boolean) = if (up) canUp else canDown
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotEmpty()) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            HoldButton("⇈", "置顶到开头", movable && dim(true), repeat = false) {
                if (movable) onEdge(true) else onBlocked()
            }
            HoldButton("▲", "上移一位", movable && dim(true), repeat = true) {
                if (movable) onMove(-1) else onBlocked()
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            HoldButton("▼", "下移一位", movable && dim(false), repeat = true) {
                if (movable) onMove(1) else onBlocked()
            }
            HoldButton("⇊", "置底到末尾", movable && dim(false), repeat = false) {
                if (movable) onEdge(false) else onBlocked()
            }
        }
    }
}

@Composable
private fun HoldButton(
    glyph: String,
    desc: String,
    enabled: Boolean,
    repeat: Boolean,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .semantics { contentDescription = desc }
            .pointerInput(desc, enabled, repeat) {
                detectTapGestures(onPress = {
                    onClick()
                    if (repeat && enabled) {
                        val job = scope.launch {
                            delay(420)
                            while (true) {
                                onClick()
                                delay(115)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
    }
}