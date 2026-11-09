package com.ifthen.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ifthen.app.ai.AiCandidate
import com.ifthen.app.data.Folder
import com.ifthen.app.data.Rule
import com.ifthen.app.data.RuleKind
import com.ifthen.app.data.RuleStatus
import com.ifthen.app.data.isResolved
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.ui.components.TitleBar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val editDueFmt = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)

/** 日期选择器给的是 UTC 零点，换算成本地当天 0 点再存 */
private fun utcMidnightToLocalDay(utc: Long): Long {
    val src = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply {
        clear()
        set(src.get(Calendar.YEAR), src.get(Calendar.MONTH), src.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(
    vm: AppViewModel,
    ruleId: Long,
    defaultFolderId: Long?,
    defaultKind: String,
    state: com.ifthen.app.ui.AppUiState,
    onDone: () -> Unit,
    onToast: (String) -> Unit,
    onPickerVisibilityChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var ifText by rememberSaveable { mutableStateOf("") }
    var thenText by rememberSaveable { mutableStateOf("") }
    var miniThen by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showKindMenu by remember { mutableStateOf(false) }
    var showNextPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    var nextCandidates by remember { mutableStateOf<List<Rule>>(emptyList()) }
    var nextId by remember { mutableStateOf<Long?>(null) }
    var kind by remember { mutableStateOf(defaultKind) }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var reminderMin by remember { mutableStateOf(-1) }
    var reminderDays by remember { mutableStateOf(0) }
    var showReminder by remember { mutableStateOf(false) }
    var showAiSection by remember { mutableStateOf(false) }
    var folderId by remember { mutableStateOf(if (ruleId > 0) null else defaultFolderId) }
    var current by remember { mutableStateOf<Rule?>(null) }

    var concern by rememberSaveable { mutableStateOf("") }
    var generating by rememberSaveable { mutableStateOf(false) }
    var aiError by rememberSaveable { mutableStateOf<String?>(null) }
    val candidates = remember { mutableStateListOf<AiCandidate>() }
    val checked = remember { mutableStateMapOf<Int, Boolean>() }
    val settings by vm.apiSettings.collectAsState()

    LaunchedEffect(showFolderPicker) { onPickerVisibilityChange(showFolderPicker) }

    LaunchedEffect(ruleId) {        if (ruleId > 0) {
            vm.getRule(ruleId)?.let {
                current = it
                ifText = it.ifText
                thenText = it.thenText
                miniThen = it.miniThen
                tags = it.tags
                folderId = it.folderId
                nextId = vm.successorOf(ruleId)
                kind = it.kind
                dueDate = it.dueDate
                reminderMin = it.reminderMin
                reminderDays = it.reminderDays
            }
        }
        loaded = true
    }

    LaunchedEffect(showNextPicker) {
        if (showNextPicker) nextCandidates = vm.linkCandidates(ruleId)
    }

    val archived = current?.status == RuleStatus.ARCHIVED.name

    fun switchKind(toOneOff: Boolean) {
        // 只切本地状态；真正落库在点「保存」时（避免切一下就把规则改了）
        kind = if (toOneOff) RuleKind.ONE_OFF.name else RuleKind.REPEATING.name
    }

    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 6.dp, bottom = 14.dp)
        ) {
            val oneOff = kind == RuleKind.ONE_OFF.name

            fun doSave() {
                if (ifText.isBlank() || thenText.isBlank()) {
                    onToast("如果/那就 都要填")
                    return
                }
                val base = current
                vm.saveRuleEdited(
                    original = current,
                    updated = (base ?: Rule(ifText = "", thenText = "")).copy(
                        ifText = ifText.trim(),
                        thenText = thenText.trim(),
                        miniThen = miniThen.trim(),
                        tags = tags.trim(),
                        folderId = folderId,
                        kind = kind,
                        dueDate = if (oneOff) dueDate else null,
                        reminderMin = if (oneOff) -1 else reminderMin,
                        reminderDays = if (oneOff) 0 else reminderDays
                    )
                ) { onDone() }
            }

            // 顶部：关闭 + 清单 + 类型 + 保存
            val curList = state.folders.firstOrNull { it.id == folderId }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDone, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .clickable(enabled = !oneOff) { showFolderPicker = true }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        curList?.name ?: "未分类",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("▾", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Box {
                    Text(
                        if (oneOff) "一次性" else "长期",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f), RoundedCornerShape(50))
                            .clickable { showKindMenu = true }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                    DropdownMenu(
                        expanded = showKindMenu,
                        onDismissRequest = { showKindMenu = false },
                        containerColor = MaterialTheme.colorScheme.background
                    ) {
                        DropdownMenuItem(text = { Text("长期预案") }, onClick = {
                            showKindMenu = false
                            if (oneOff) switchKind(false)
                        })
                        DropdownMenuItem(text = { Text("一次性") }, onClick = {
                            showKindMenu = false
                            if (!oneOff) switchKind(true)
                        })
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { doSave() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = "保存", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
            }

            // 日期与提醒（单色图标 + 轻箭头）
            run {
                val unset = if (oneOff) dueDate == null else reminderMin < 0
                val txt = if (oneOff) {
                    dueDate?.let { "${editDueFmt.format(Date(it))} 前" } ?: "日期与提醒"
                } else {
                    if (reminderMin < 0) "日期与提醒" else com.ifthen.app.reminder.ReminderScheduler.label(reminderMin, reminderDays)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { if (oneOff) showDuePicker = true else showReminder = true }
                        .padding(horizontal = 2.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(11.dp))
                    Text(
                        txt,
                        fontSize = 15.sp,
                        fontWeight = if (unset) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (unset) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
            }

            // 如果 / 那就 / 两分钟版
            Column(
                Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                NotchField(
                    value = ifText,
                    onValueChange = { ifText = it },
                    label = "如果",
                    placeholder = "触发情境：时间 / 地点 / 情绪 / 动作",
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                NotchField(
                    value = thenText,
                    onValueChange = { thenText = it },
                    label = "那就",
                    placeholder = "立刻做的一件小事",
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 28.sp
                    ),
                    minLines = 2
                )
                NotchField(
                    value = miniThen,
                    onValueChange = { miniThen = it },
                    label = "两分钟版",
                    placeholder = "可选，状态差时只做这一句",
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    compact = true
                )
            }

            // 清单 / 到期 / 连接
            if (ruleId > 0 && !oneOff) {
                state.chain.chainPos[ruleId]?.let { pos ->
                    Text(
                        "这条排在链条第 ${pos.step}/${pos.size} 步（首页点卡片上的序号可以从这里断开）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 14.dp)
                    )
                }
            }

            if (current?.isResolved == true) {
                OutlinedButton(
                    onClick = { vm.reopenRule(ruleId) { onToast(it); onDone() } },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) { Text("重新挂起为待结清") }
            }

            // AI 辅助
            if (showAiSection) Column(
                Modifier.padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditCard {
                    Text(
                        "描述一个困扰，AI 只出候选，勾选确认后才入库",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = concern,
                        onValueChange = { concern = it },
                        label = { Text("例如：一到下午就犯困想刷手机") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (settings.apiKey.isBlank()) {
                        Text(
                            "未配置 API Key：去设置页填写",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = concern.isNotBlank() && !generating,
                            onClick = {
                                generating = true
                                aiError = null
                                candidates.clear()
                                vm.generateCandidates(concern) { r ->
                                    generating = false
                                    r.onSuccess { candidates.addAll(it) }
                                        .onFailure { aiError = it.message ?: "生成失败" }
                                }
                            }
                        ) { Text("生成候选") }
                        if (generating) {
                            Spacer(Modifier.size(12.dp))
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    aiError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    candidates.forEachIndexed { i, c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked[i] == true,
                                onCheckedChange = { checked[i] = it }
                            )
                            Column(Modifier.weight(1f)) {
                                Text("如果 ${c.ifText}", style = MaterialTheme.typography.bodyMedium)
                                Text("→ ${c.thenText}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (candidates.isNotEmpty()) {
                        Button(
                            enabled = checked.values.count { it } > 0,
                            onClick = {
                                val picked = candidates.filterIndexed { i, _ -> checked[i] == true }
                                vm.addCandidates(picked, folderId, kind)
                                candidates.clear()
                                checked.clear()
                                onToast("已入库 ${picked.size} 条")
                            }
                        ) { Text("入库所选") }
                    }
                }
            }
            // 底部工具条：左 AI / 下一步，右 归档 / 删除
            HorizontalDivider(
                modifier = Modifier.padding(top = 20.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    ToolbarIcon(
                        Icons.Filled.AutoAwesome,
                        "AI",
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    ) { showAiSection = !showAiSection }
                    if (ruleId > 0 && loaded && !oneOff) {
                        ToolbarIcon(
                            Icons.Filled.Link,
                            "下一步",
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        ) { showNextPicker = true }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    if (ruleId > 0 && loaded) {
                        ToolbarIcon(
                            Icons.Filled.Archive,
                            if (archived) "恢复" else "归档",
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        ) {
                            current?.let {
                                vm.setArchived(it, !archived)
                                onToast(if (archived) "已恢复为活跃" else "已归档")
                            }
                        }
                        ToolbarIcon(Icons.Filled.DeleteOutline, "删除", Color(0xFF9E3F1E)) { showDelete = true }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    if (showFolderPicker) {
        MoveToListPicker(
            folders = state.folders,
            currentId = folderId,
            onDismiss = { showFolderPicker = false },
            onSelect = { id ->
                folderId = id
                showFolderPicker = false
            },
            onCreate = { name, onCreated ->
                vm.createFolder(name, "📋", 0xFF2B4CE0, parentId = null) { id -> onCreated(id) }
            }
        )
    }

    if (showNextPicker) {
        AlertDialog(
            onDismissRequest = { showNextPicker = false },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("做完这条之后接哪个？") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    if (nextCandidates.isEmpty()) {
                        Text(
                            "没有可接的预案：只有还没被别的预案接住的活跃预案能接上，且接上去不能形成循环。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        nextCandidates.forEach { c ->
                            TextButton(
                                onClick = {
                                    vm.linkNext(ruleId, c.id) { msg -> msg?.let(onToast) }
                                    nextId = c.id
                                    showNextPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "如果 ${c.ifText}" + if (c.id == nextId) "（当前）" else "",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Row {
                    if (nextId != null) {
                        TextButton(onClick = {
                            vm.linkNext(ruleId, null) { msg -> msg?.let(onToast) }
                            nextId = null
                            showNextPicker = false
                        }) { Text("取消连接") }
                    }
                    TextButton(onClick = { showNextPicker = false }) { Text("关闭") }
                }
            }
        )
    }

    if (showDuePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate?.let {
                java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = it
                    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDuePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { dueDate = utcMidnightToLocalDay(it) }
                    showDuePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                Row {
                    if (dueDate != null) {
                        TextButton(onClick = { dueDate = null; showDuePicker = false }) { Text("不限") }
                    }
                    TextButton(onClick = { showDuePicker = false }) { Text("取消") }
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showReminder) {
        ReminderDialog(
            initialMin = reminderMin,
            initialDays = reminderDays,
            onDismiss = { showReminder = false }
        ) { m, d ->
            reminderMin = m
            reminderDays = d
            showReminder = false
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("彻底删除？") },
            text = { Text("这条预案和它的全部打卡记录会被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    current?.let { r ->
                        vm.deleteRule(r) { msg ->
                            showDelete = false
                            onToast(msg)
                            onDone()
                        }
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MoveToListPicker(
    folders: List<Folder>,
    currentId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit,
    onCreate: (String, (Long) -> Unit) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val bg = MaterialTheme.colorScheme.background
    val blue = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = Color(0xFF9A958B)
    val searchBg = Color(0xFFECE8E0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x2E14141A))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = bg,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    Modifier
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "关闭",
                                tint = ink,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Text("移动到", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.2.sp)
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(searchBg)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = muted,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, color = ink),
                            cursorBrush = SolidColor(blue),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text("搜索", fontSize = 15.sp, color = muted)
                                inner()
                            }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val q = query.trim()
                        if (q.isBlank() || "未分类".contains(q, true)) {
                            MoveToListRow(
                                icon = {
                                    Icon(
                                        Icons.Outlined.Inbox,
                                        contentDescription = null,
                                        tint = Color(0xFF6B665E),
                                        modifier = Modifier.size(21.dp)
                                    )
                                },
                                name = "未分类",
                                selected = currentId == null,
                                tint = blue
                            ) { onSelect(null) }
                        }
                        folders.filter { q.isBlank() || it.name.contains(q, true) }.forEach { f ->
                            MoveToListRow(
                                icon = { Text(if (f.emoji.isBlank()) "📋" else f.emoji, fontSize = 19.sp) },
                                name = f.name,
                                selected = currentId == f.id,
                                tint = blue
                            ) { onSelect(f.id) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable { newName = ""; adding = true }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = blue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("添加清单", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = blue)
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = bg,
            title = { Text("新建清单") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("清单名") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        val n = newName.trim()
                        adding = false
                        onCreate(n) { id -> onSelect(id) }
                    }
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun MoveToListRow(
    icon: @Composable () -> Unit,
    name: String,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(14.dp))
        Text(
            name,
            fontSize = 16.5.sp,
            letterSpacing = 0.1.sp,
            color = Color(0xFF1A1A1E),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "当前清单", tint = tint, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF5F5B54))
    }
}

/**
 * 带缺口标签的细边框输入框，尺寸对齐编辑弹层 demo：
 * 12dp 圆角、1dp 边框、标签压在边框上、14/13dp 内边距。
 */
@Composable
private fun NotchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    compact: Boolean = false
) {
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.17f)
    val bg = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = if (compact) 13.dp else 14.dp,
                    bottom = if (compact) 11.dp else 13.dp
                )
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = singleLine,
                minLines = minLines,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = if (singleLine) 1 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            )
        }
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 11.dp, y = (-8).dp)
                .background(bg)
                .padding(horizontal = 7.dp)
        )
    }
}

@Composable
private fun AccentField(accent: Color, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(2.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun ReminderDialog(
    initialMin: Int,
    initialDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var hour by remember { mutableStateOf(if (initialMin >= 0) initialMin / 60 else 9) }
    var minute by remember { mutableStateOf(if (initialMin >= 0) initialMin % 60 else 0) }
    var mask by remember { mutableStateOf(if (initialDays == 0) 127 else initialDays) }
    var custom by remember { mutableStateOf(initialDays != 0 && initialDays != 127 && initialDays != 31) }
    val hh = hour.toString().padStart(2, '0')
    val mm = minute.toString().padStart(2, '0')
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("定时提醒") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        android.app.TimePickerDialog(
                            context,
                            { _, h, m -> hour = h; minute = m },
                            hour, minute, true
                        ).show()
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("时间", Modifier.weight(1f))
                    Text("$hh:$mm", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "重复",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !custom && mask == 127, onClick = { mask = 127; custom = false }, label = { Text("每天") })
                    FilterChip(selected = !custom && mask == 31, onClick = { mask = 31; custom = false }, label = { Text("工作日") })
                    FilterChip(selected = custom, onClick = { custom = true; if (mask == 127 || mask == 31) mask = 127 }, label = { Text("自定义") })
                }
                if (custom) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val names = listOf("一", "二", "三", "四", "五", "六", "日")
                        names.forEachIndexed { i, n ->
                            val on = (mask shr i) and 1 == 1
                            FilterChip(
                                selected = on,
                                onClick = { mask = if (on) mask and (1 shl i).inv() else mask or (1 shl i) },
                                label = { Text(n) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour * 60 + minute, if (mask == 0) 127 else mask) }) { Text("确定") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onConfirm(-1, 0) }) { Text("关闭提醒") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun EditSectionHeader(title: String, icon: ImageVector) {
    Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EditCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun PickRow(
    icon: ImageVector,
    title: String,
    value: String,
    action: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(action, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
