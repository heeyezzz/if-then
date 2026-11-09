package com.ifthen.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ifthen.app.R
import com.ifthen.app.data.RuleKind
import com.ifthen.app.ui.AppUiState
import com.ifthen.app.ui.AppViewModel
import kotlinx.coroutines.launch

/**
 * 「任务」总入口：左侧抽屉 = 智能清单（长期预案 / 一次性）+ 自定义清单（复用文件夹表，扁平）。
 * 选中某清单只显示该清单的长期预案；「长期预案」显示全部。主区仍是原来的长期或一次性列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    vm: AppViewModel,
    state: AppUiState,
    oneOff: Boolean,
    onSelectOneOff: (Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onEditChain: (Long) -> Unit,
    onRecord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showNewList by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    fun closeDrawer() { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            TaskDrawer(
                state = state,
                oneOff = oneOff,
                onSelectLong = {
                    onSelectOneOff(false); vm.openFolder(null); closeDrawer()
                },
                onSelectOneOff = {
                    onSelectOneOff(true); closeDrawer()
                },
                onSelectList = { id ->
                    onSelectOneOff(false); vm.openFolder(id); closeDrawer()
                },
                onNewList = {
                    closeDrawer(); newListName = ""; showNewList = true
                }
            )
        }
    ) {
        if (oneOff) {
            OneOffScreen(
                vm, state,
                onEdit = onEdit,
                onRecord = onRecord,
                onOpenDrawer = openDrawer
            )
        } else {
            HomeScreen(
                vm, state,
                onEdit = onEdit,
                onEditChain = onEditChain,
                onRecord = onRecord,
                onOpenDrawer = openDrawer
            )
        }
    }

    if (showNewList) {
        AlertDialog(
            onDismissRequest = { showNewList = false },
            title = { Text("新建清单") },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text("清单名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newListName.isNotBlank(),
                    onClick = {
                        vm.createFolder(newListName.trim(), "📋", 0xFF2B4CE0)
                        showNewList = false
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewList = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TaskDrawer(
    state: AppUiState,
    oneOff: Boolean,
    onSelectLong: () -> Unit,
    onSelectOneOff: () -> Unit,
    onSelectList: (Long) -> Unit,
    onNewList: () -> Unit
) {
    val ltCount = state.activeRules.count { it.kind != RuleKind.ONE_OFF.name }
    val ooCount = state.activeRules.count { it.kind == RuleKind.ONE_OFF.name }
    val lists = state.folders

    ModalDrawerSheet {
        Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 16.dp)) {
            Row(
                Modifier.padding(start = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_clock),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(12.dp))
                Text("布谷", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }

            DrawerRow(
                label = "长期预案",
                count = ltCount,
                selected = !oneOff && state.currentFolderId == null,
                onClick = onSelectLong
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.List, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)
                )
            }
            DrawerRow(
                label = "一次性",
                count = ooCount,
                selected = oneOff,
                onClick = onSelectOneOff
            ) {
                Icon(
                    Icons.Filled.TaskAlt, contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    .height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "清单",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
            )
            lists.forEach { f ->
                DrawerRow(
                    label = f.name,
                    count = state.activeRules.count { it.folderId == f.id && it.kind != RuleKind.ONE_OFF.name },
                    selected = !oneOff && state.currentFolderId == f.id,
                    onClick = { onSelectList(f.id) }
                ) {
                    Text(f.emoji, style = MaterialTheme.typography.titleMedium)
                }
            }
            DrawerRow(
                label = "新建清单",
                count = null,
                selected = false,
                onClick = onNewList
            ) {
                Icon(
                    Icons.Filled.Add, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerRow(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Text(
                "$count",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
