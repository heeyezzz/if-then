package com.ifthen.app.ui.components

import android.content.ClipDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ifthen.app.data.Folder
import com.ifthen.app.data.Rule
import com.ifthen.app.ui.RuleStat

val FolderEmojis = listOf("📁", "🌙", "☀️", "💼", "🏋️", "📚", "🍔", "🧘", "✈️", "🌱")
val FolderColors = listOf(0xFF1F6E5C, 0xFF2F5DA8, 0xFF6A4FA3, 0xFFB8860B, 0xFF8C4A2F, 0xFF0F7B7B)

@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StreakChip(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "连击 $count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * 点=做了；长按=这次没做；把手图标长按拖拽换文件夹。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RuleCard(
    rule: Rule,
    stat: RuleStat,
    emphasized: Boolean = false,
    dragSource: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = if (emphasized) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = colors
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "如果 " + rule.ifText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "长按拖到文件夹里",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(30.dp)
                        .then(dragSource)
                )
                Text(
                    text = "编辑",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(onClick = onEdit, onLongClick = null)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "→ " + rule.thenText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                StreakChip(stat.streak)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCard(
    folder: Folder,
    ruleCount: Int,
    onOpen: () -> Unit,
    onManage: () -> Unit,
    onDropped: (ruleId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOver by remember { mutableStateOf(false) }
    val accent = Color(folder.color)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { dragOver = true }
            override fun onExited(event: DragAndDropEvent) { dragOver = false }
            override fun onEnded(event: DragAndDropEvent) { dragOver = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragOver = false
                val text = event.toAndroidDragEvent().clipData?.getItemAt(0)?.text?.toString()
                val id = text?.removePrefix("ifthen-rule:")?.toLongOrNull() ?: return false
                onDropped(id)
                return true
            }
        }
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { startEvent ->
                    startEvent.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                },
                target = target
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (dragOver) accent.copy(alpha = 0.35f)
            else accent.copy(alpha = 0.10f)
        ),
        border = if (dragOver) BorderStroke(2.dp, accent) else null
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onOpen, onLongClick = onManage)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(folder.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "$ruleCount 条预案 · 长按管理",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
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
fun Breadcrumbs(
    crumbs: List<Folder>,
    onRoot: () -> Unit,
    onCrumb: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "全部",
            style = MaterialTheme.typography.labelLarge,
            color = if (crumbs.isEmpty()) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = crumbs.isNotEmpty()) { onRoot() }
                .padding(4.dp)
        )
        crumbs.forEachIndexed { i, f ->
            Text(
                "›",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                "${f.emoji}${f.name}",
                style = MaterialTheme.typography.labelLarge,
                color = if (i == crumbs.lastIndex) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = i != crumbs.lastIndex) { onCrumb(f.id) }
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun Heatmap(counts: List<Int>, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val low = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val mid = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val high = MaterialTheme.colorScheme.primary
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        counts.chunked(10).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c ->
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
                            .background(level)
                    )
                }
                repeat(10 - row.size) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}
