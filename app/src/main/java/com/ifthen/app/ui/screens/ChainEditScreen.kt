package com.ifthen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ifthen.app.Constants
import com.ifthen.app.data.Folder
import com.ifthen.app.data.Rule
import com.ifthen.app.ui.AppUiState
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.ui.ChainPos

/**
 * 整串建链 / 整串编辑。一行一步，「连着」把两步接上，「已拆开」让后半串独立成链。
 * id=0 的行是还没入库的新步骤；uid 只为在插入和换位时保住输入焦点。
 */
private data class StepRow(
    val uid: Long,
    val id: Long,
    val ifText: String,
    val thenText: String,
    val broken: Boolean,
    val note: String? = null
)

@Composable
fun ChainEditScreen(
    vm: AppViewModel,
    headId: Long,
    defaultFolderId: Long?,
    state: AppUiState,
    onDone: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember { mutableStateListOf<StepRow>() }
    var uidSeed by remember { mutableStateOf(1L) }
    var folderId by remember { mutableStateOf(defaultFolderId) }
    var chainName by remember { mutableStateOf("") }
    var initialName by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(headId <= 0L) }
    var initial by remember { mutableStateOf<List<StepRow>>(emptyList()) }
    var initialFolder by remember { mutableStateOf<Long?>(defaultFolderId) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf(0L) }
    var showReview by remember { mutableStateOf(false) }
    val focusers = remember { HashMap<Long, FocusRequester>() }
    var pendingFocus by remember { mutableStateOf(0L) }

    if (headId <= 0L && rows.isEmpty()) {
        repeat(2) { rows.add(StepRow(uidSeed++, 0L, "", "", false)) }
    }
    LaunchedEffect(headId) {
        if (headId > 0L) {
            vm.chainSteps(headId).forEach {
                rows.add(StepRow(uidSeed++, it.id, it.ifText, it.thenText, it.broken))
            }
            initial = rows.toList()
            initialFolder = state.ruleById[headId]?.folderId
            folderId = initialFolder
            val cid = state.ruleById[headId]?.chainId
            chainName = cid?.let { state.chainById[it]?.name } ?: ""
            initialName = chainName
            loaded = true
        }
    }

    val incomplete = rows.indexOfFirst { it.ifText.isBlank() || it.thenText.isBlank() }
    val canSave = loaded && rows.isNotEmpty() && incomplete < 0

    fun addRow(after: Int) {
        val r = StepRow(uidSeed++, 0L, "", "", false)
        rows.add(after + 1, r)
        pendingFocus = r.uid
    }

    fun move(i: Int, delta: Int) {
        val to = i + delta
        if (to in rows.indices) rows.add(to, rows.removeAt(i))
    }

    /** 按 uid 定位再改：列表换序后组合期捕获的下标可能是旧的，直接写 rows[i] 会串到别的行 */
    fun patch(uid: Long, f: (StepRow) -> StepRow) {
        val k = rows.indexOfFirst { it.uid == uid }
        if (k >= 0) rows[k] = f(rows[k])
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    Text(
                        if (headId > 0L) "整串编辑" else "新建一串",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "一行一步，做完上一步就接下一步。整串进同一个文件夹。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = chainName,
                    onValueChange = { chainName = it },
                    label = { Text("这条链叫什么（留空自动用第一句「如果」）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.clickable { showFolderPicker = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("整串放进", style = MaterialTheme.typography.labelLarge)
                            Text(
                                state.folders.firstOrNull { it.id == folderId }
                                    ?.let { "${it.emoji}${it.name}" } ?: "根目录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "更换 ›",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            rows.forEachIndexed { i, row ->
                if (i > 0) {
                    item(key = "gap${row.uid}") {
                        GapDivider(broken = row.broken) {
                            patch(row.uid) { r -> r.copy(broken = !r.broken) }
                        }
                    }
                }
                item(key = "r${row.uid}") {
                    StepCard(
                        index = stepIndex(rows, i),
                        row = row,
                        canUp = i > 0,
                        canDown = i < rows.lastIndex,
                        focusRequester = focusers.getOrPut(row.uid) { FocusRequester() },
                        autoFocus = row.uid == pendingFocus,
                        onFocused = { pendingFocus = 0L },
                        onIf = { v -> patch(row.uid) { r -> r.copy(ifText = v) } },
                        onThen = { v -> patch(row.uid) { r -> r.copy(thenText = v) } },
                        onThenDone = { if (i == rows.lastIndex) addRow(i) },
                        onUp = { move(i, -1) },
                        onDown = { move(i, +1) },
                        onPick = { pickFor = row.uid },
                        onRemove = {
                            rows.removeAt(i)
                            focusers.remove(row.uid)
                        }
                    )
                }
            }
            item {
                Button(onClick = { addRow(rows.lastIndex) }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ 再写一步")
                }
            }
            if (rows.size > Constants.CHAIN_MAX_STEPS) {
                item {
                    Text(
                        "这条已有 ${rows.size} 步，超过 ${Constants.CHAIN_MAX_STEPS} 步建议拆成两条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            if (incomplete >= 0) {
                val loud = rows.any { it.ifText.isNotBlank() != it.thenText.isNotBlank() }
                item {
                    Text(
                        "第 ${incomplete + 1} 步还没填完：「如果」和「那就」都要写",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (loud) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Button(
                    enabled = canSave,
                    onClick = { showReview = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (headId > 0L) "保存改动" else "建这条链") }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showFolderPicker) {
        FolderPicker(state.folders, folderId, onPick = { folderId = it }, onDismiss = { showFolderPicker = false })
    }

    if (pickFor != 0L) {
        val uid = pickFor
        PickExisting(
            vm = vm,
            state = state,
            usedIds = rows.mapNotNull { it.id.takeIf { id -> id > 0L } }.toSet(),
            onPick = { r, note ->
                patch(uid) { it.copy(id = r.id, ifText = r.ifText, thenText = r.thenText, note = note) }
                pickFor = 0L
            },
            onDismiss = { pickFor = 0L }
        )
    }

    if (showReview) {
        val diff = diffOf(initial, rows.toList(), initialFolder, folderId).toMutableList()
        val wantName = chainName.trim()
        if (wantName.isNotEmpty() && wantName != initialName.trim()) diff.add(0, "改名为「$wantName」")
        AlertDialog(
            onDismissRequest = { showReview = false },
            title = { Text("要写进数据库的改动") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (diff.isEmpty()) Text("只是改了文字。")
                    diff.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showReview = false
                    vm.saveChain(
                        rows.map { AppViewModel.ChainStep(it.id, it.ifText, it.thenText, it.broken) },
                        chainName,
                        folderId
                    ) { msg -> onToast(msg); onDone() }
                }) { Text("确认保存") }
            },
            dismissButton = { TextButton(onClick = { showReview = false }) { Text("再看看") } }
        )
    }
}

/** 段内序号：断开处重新计数，因为断开后就是另一条链 */
private fun stepIndex(rows: List<StepRow>, i: Int): Int {
    var n = 0
    for (k in 1..i) if (!rows[k].broken) n++
    return n + 1
}

private fun diffOf(
    before: List<StepRow>,
    after: List<StepRow>,
    folder0: Long?,
    folder1: Long?
): List<String> {
    val out = ArrayList<String>()
    val kept = before.filter { it.id > 0L && after.any { a -> a.id == it.id } }
    val added = after.count { it.id == 0L }
    val removed = before.count { it.id > 0L && after.none { a -> a.id == it.id } }
    val reworded = kept.count { b -> after.first { it.id == b.id }.let { it.ifText != b.ifText || it.thenText != b.thenText } }
    val reordered = kept.map { b -> after.indexOfFirst { it.id == b.id } } != kept.indices.toList()
    if (added > 0) out.add("新建 $added 步")
    if (reworded > 0) out.add("改写 $reworded 步的文字")
    if (removed > 0) out.add("摘出 $removed 步（预案本身留在文件夹里）")
    if (reordered) out.add("调整了步骤顺序")
    val breaks = after.count { it.broken }
    if (breaks > 0) out.add("拆成 ${breaks + 1} 段")
    if (folder0 != folder1) out.add("整串换文件夹")
    return out
}

@Composable
private fun GapDivider(broken: Boolean, onToggle: () -> Unit) {
    val color = if (broken) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).height(2.dp)
                .background(color.copy(alpha = if (broken) 0.35f else 0.45f), RoundedCornerShape(1.dp))
        )
        TextButton(
            onClick = onToggle,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text(
                if (broken) "已拆开" else "连着",
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
        Box(
            Modifier.weight(1f).height(2.dp)
                .background(color.copy(alpha = if (broken) 0.35f else 0.45f), RoundedCornerShape(1.dp))
        )
    }
}

@Composable
private fun StepCard(
    index: Int,
    row: StepRow,
    canUp: Boolean,
    canDown: Boolean,
    focusRequester: FocusRequester,
    autoFocus: Boolean,
    onFocused: () -> Unit,
    onIf: (String) -> Unit,
    onThen: (String) -> Unit,
    onThenDone: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onPick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // 新加的行要滚进视野并拿到焦点：直接 requestFocus 在节点挂上前是空操作
        LaunchedEffect(autoFocus) {
            if (autoFocus) {
                focusRequester.requestFocus()
                onFocused()
            }
        }
        // 只有一边填了一边空着才算错，且只标空的那个框：整页刚打开时不该满屏飘红
        val partial = row.ifText.isNotBlank() != row.thenText.isNotBlank()
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .semantics { contentDescription = "第 $index 步" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$index",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (row.id > 0L) "已有预案" else "新建",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                StepArrow("▲", "上移一步", enabled = canUp, onClick = onUp)
                StepArrow("▼", "下移一步", enabled = canDown, onClick = onDown)
                TextButton(
                    onClick = onPick,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("换已有", style = MaterialTheme.typography.labelMedium) }
                TextButton(
                    onClick = onRemove,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("摘出", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary)
                }
            }
            OutlinedTextField(
                value = row.ifText,
                onValueChange = onIf,
                label = { Text("如果…") },
                singleLine = true,
                isError = partial && row.ifText.isBlank(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = row.thenText,
                onValueChange = onThen,
                label = { Text("那就…") },
                singleLine = true,
                isError = partial && row.thenText.isBlank(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onThenDone() }),
                modifier = Modifier.fillMaxWidth()
            )
            row.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StepArrow(glyph: String, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent
            )
            .semantics { contentDescription = desc }
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    }
}

/** 挑一条已有预案接进这一串：标清它现在在哪条链第几步，选了会把那条链剪断 */
@Composable
private fun PickExisting(
    vm: AppViewModel,
    state: AppUiState,
    usedIds: Set<Long>,
    onPick: (Rule, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var cands by remember { mutableStateOf<List<Pair<Rule, ChainPos?>>?>(null) }
    LaunchedEffect(Unit) { cands = vm.chainPickable(usedIds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("这一步用哪条已有预案？") },
        text = {
            Column(
                Modifier.fillMaxWidth().height(360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "选一条已在清单里的预案。它原来所在的那条链会在它这里剪断，前后两步直接接上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                cands?.forEach { (r, p) ->
                    TextButton(
                        onClick = { onPick(r, p?.let { "接进来会剪断它原来那条链" }) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(r.ifText, modifier = Modifier.fillMaxWidth())
                            if (p != null) {
                                Text(
                                    "现在在「${state.ruleById[p.headId]?.ifText ?: "某条链"}」第 ${p.step}/${p.size} 步",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                if (cands?.isEmpty() == true) {
                    Text("没有可选的已有预案。", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun FolderPicker(
    folders: List<Folder>,
    current: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val rows = ArrayList<Pair<Int, Folder?>>()
    rows.add(0 to null)
    val childrenOf = folders.groupBy { it.parentId }
    fun walk(parentId: Long?, depth: Int) {
        childrenOf[parentId].orEmpty().forEach { f ->
            rows.add(depth to f); walk(f.id, depth + 1)
        }
    }
    walk(null, 1)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("整串放进哪个文件夹？") },
        text = {
            Column {
                rows.forEach { (depth, f) ->
                    TextButton(
                        onClick = { onPick(f?.id); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = (8 + depth * 20).dp)
                    ) {
                        Text(
                            if (f == null) "根目录" else "${f.emoji}${f.name}",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
