package com.ifthen.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.ifthen.app.ai.AiCandidate
import com.ifthen.app.data.Rule
import com.ifthen.app.data.RuleKind
import com.ifthen.app.data.RuleStatus
import com.ifthen.app.data.isResolved
import com.ifthen.app.ui.AppViewModel
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
    modifier: Modifier = Modifier
) {
    var ifText by rememberSaveable { mutableStateOf("") }
    var thenText by rememberSaveable { mutableStateOf("") }
    var miniThen by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showNextPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    var nextCandidates by remember { mutableStateOf<List<Rule>>(emptyList()) }
    var nextId by remember { mutableStateOf<Long?>(null) }
    var kind by remember { mutableStateOf(defaultKind) }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var folderId by remember { mutableStateOf(if (ruleId > 0) null else defaultFolderId) }
    var current by remember { mutableStateOf<Rule?>(null) }

    var concern by rememberSaveable { mutableStateOf("") }
    var generating by rememberSaveable { mutableStateOf(false) }
    var aiError by rememberSaveable { mutableStateOf<String?>(null) }
    val candidates = remember { mutableStateListOf<AiCandidate>() }
    val checked = remember { mutableStateMapOf<Int, Boolean>() }
    val settings by vm.apiSettings.collectAsState()

    LaunchedEffect(ruleId) {
        if (ruleId > 0) {
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
            }
        }
        loaded = true
    }

    LaunchedEffect(showNextPicker) {
        if (showNextPicker) nextCandidates = vm.linkCandidates(ruleId)
    }

    val archived = current?.status == RuleStatus.ARCHIVED.name

    fun switchKind(toOneOff: Boolean) {
        if (ruleId > 0) vm.convertKind(ruleId, toOneOff) { onToast(it) }
        kind = if (toOneOff) RuleKind.ONE_OFF.name else RuleKind.REPEATING.name
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (ruleId > 0) "编辑预案" else "新建预案",
                style = MaterialTheme.typography.headlineSmall
            )
            val oneOff = kind == RuleKind.ONE_OFF.name
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !oneOff,
                    onClick = { if (oneOff) switchKind(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("长期") }
                SegmentedButton(
                    selected = oneOff,
                    onClick = { if (!oneOff) switchKind(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("一次性") }
            }
            Text(
                if (oneOff) "情境只来一次：做完或没做都当场结清，然后从清单消失"
                else "反复照案执行：留在清单里，触发一次打卡一次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = ifText,
                onValueChange = { ifText = it },
                label = { Text("如果…（触发情境：时间/地点/情绪/动作）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            OutlinedTextField(
                value = thenText,
                onValueChange = { thenText = it },
                label = { Text("那就…（立刻可做的一件小事）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            OutlinedTextField(
                value = miniThen,
                onValueChange = { miniThen = it },
                label = { Text("两分钟版（可选）") },
                supportingText = {
                    Text("状态差时只做这一句也算「做了」：先保住动作本身，再谈剂量")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (!oneOff) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.clickable { showFolderPicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("所在文件夹", style = MaterialTheme.typography.labelLarge)
                            val f = state.folders.firstOrNull { it.id == folderId }
                            Text(
                                if (f == null) "根目录" else "${f.emoji}${f.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("更换 ›", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (oneOff) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.clickable { showDuePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("哪天之前", style = MaterialTheme.typography.labelLarge)
                            Text(
                                dueDate?.let { "${editDueFmt.format(java.util.Date(it))} 前" }
                                    ?: "不限 · 情境出现就做",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (dueDate == null) "设定 ›" else "更换 ›",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (ruleId > 0 && loaded && !oneOff) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.clickable { showNextPicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("下一步接哪个预案", style = MaterialTheme.typography.labelLarge)
                            val nx = nextId?.let { id -> state.ruleById[id] }
                            Text(
                                if (nx == null) "没有 · 做完这条就结束" else "→ ${nx.ifText}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (nextId == null) "接上 ›" else "更换 ›",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                state.chain.chainPos[ruleId]?.let { pos ->
                    Text(
                        "这条排在链条第 ${pos.step}/${pos.size} 步（首页点卡片上的序号可以从这里断开）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = {
                    if (ifText.isBlank() || thenText.isBlank()) {
                        onToast("如果/那就 都要填")
                        return@Button
                    }
                    val base = current
                    vm.saveRule(
                        (base ?: Rule(ifText = "", thenText = "")).copy(
                            ifText = ifText.trim(),
                            thenText = thenText.trim(),
                            miniThen = miniThen.trim(),
                            tags = tags.trim(),
                            folderId = folderId,
                            kind = kind,
                            dueDate = if (oneOff) dueDate else null
                        )
                    ) { onDone() }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }

            if (ruleId > 0 && loaded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            current?.let {
                                vm.setArchived(it, !archived)
                                onToast(if (archived) "已恢复为活跃" else "已归档")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (archived) "恢复激活" else "归档这条") }
                    OutlinedButton(
                        onClick = { showDelete = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("彻底删除") }
                }
            }
            if (current?.isResolved == true) {
                OutlinedButton(
                    onClick = { vm.reopenRule(ruleId) { onToast(it); onDone() } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("重新挂起为待结清") }
            }

            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("让 AI 帮你想", style = MaterialTheme.typography.titleMedium)
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
                        minLines = 2
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
            Spacer(Modifier.height(60.dp))
        }
    }

    if (showFolderPicker) {
        val rows = ArrayList<Pair<Int, com.ifthen.app.data.Folder?>>()
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
            onDismissRequest = { showFolderPicker = false },
            title = { Text("移到哪个文件夹？") },
            text = {
                Column {
                    rows.forEach { (depth, f) ->
                        TextButton(
                            onClick = { folderId = f?.id; showFolderPicker = false },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = (8 + depth * 20).dp
                            )
                        ) {
                            Text(
                                if (f == null) "根目录" else "${f.emoji}${f.name}",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showFolderPicker = false }) { Text("取消") } }
        )
    }

    if (showNextPicker) {
        AlertDialog(
            onDismissRequest = { showNextPicker = false },
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

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
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
