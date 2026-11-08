package com.ifthen.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ifthen.app.BuildConfig
import com.ifthen.app.R
import com.ifthen.app.ui.AppViewModel
import com.ifthen.app.update.UpdateInfo

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by vm.apiSettings.collectAsState()
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var confirmImport by rememberSaveable { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var testing by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var latestInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(settings, initialized) {
        if (!initialized && settings.baseUrl.isNotBlank()) {
            baseUrl = settings.baseUrl
            apiKey = settings.apiKey
            model = settings.model
            initialized = true
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { u ->
            vm.exportData(u) { r ->
                r.onSuccess { onToast("已导出 $it 条预案") }
                    .onFailure { onToast("导出失败：${it.message}") }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = it
            confirmImport = true
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

            // ── AI 助手 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("AI 助手", Icons.Filled.SmartToy)
                SectionCard {
                    Text(
                        "BYOK · OpenAI 兼容协议",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = baseUrl, onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = model, onValueChange = { model = it },
                        label = { Text("模型名") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                vm.saveApiSettings(
                                    com.ifthen.app.data.ApiSettings(baseUrl.trim(), apiKey.trim(), model.trim())
                                )
                                onToast("已保存")
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("保存") }
                        OutlinedButton(
                            enabled = !testing,
                            onClick = {
                                testing = true
                                vm.testApi(
                                    com.ifthen.app.data.ApiSettings(baseUrl.trim(), apiKey.trim(), model.trim())
                                ) { r ->
                                    testing = false
                                    r.onSuccess { onToast("连接成功，可见 $it 个模型") }
                                        .onFailure { onToast("连接失败：${it.message}") }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            if (testing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("  测试中…")
                            } else {
                                Text("测试连接")
                            }
                        }
                    }
                }
            }

            // ── 数据备份 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("数据备份", Icons.Filled.Backup)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        SettingsRow(
                            icon = Icons.Filled.CloudUpload,
                            title = "导出备份",
                            subtitle = "导出为 JSON，不含 API Key",
                            onClick = { exportLauncher.launch("ifthen-backup.json") }
                        )
                        ThinDivider()
                        SettingsRow(
                            icon = Icons.Filled.CloudDownload,
                            title = "从备份恢复",
                            subtitle = "会用备份覆盖当前全部数据",
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            }
                        )
                    }
                }
            }

            // ── 关于 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("关于", Icons.Filled.Info)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_clock),
                                contentDescription = null,
                                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("布谷", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "版本 v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        ThinDivider()
                        SettingsRow(
                            icon = Icons.Filled.SystemUpdate,
                            title = "检查更新",
                            subtitle = "从 GitHub 获取最新版本",
                            trailing = {
                                if (checking) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            onClick = {
                                if (!checking) {
                                    checking = true
                                    vm.checkUpdate(BuildConfig.VERSION_NAME) { r ->
                                        checking = false
                                        r.onSuccess { info ->
                                            if (info.isNewer) {
                                                latestInfo = info
                                                showUpdate = true
                                            } else {
                                                onToast("已是最新版本 v${BuildConfig.VERSION_NAME}")
                                            }
                                        }.onFailure { onToast("检查更新失败：${it.message}") }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("确认覆盖导入？") },
            text = { Text("当前所有预案和打卡记录将被备份文件替换，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    pendingImportUri?.let { u ->
                        vm.importData(u) { r ->
                            r.onSuccess { onToast("已导入 $it 条预案") }
                                .onFailure { onToast("导入失败：${it.message}") }
                        }
                    }
                }) { Text("覆盖导入") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("取消") } }
        )
    }

    if (showUpdate && latestInfo != null) {
        val info = latestInfo!!
        AlertDialog(
            onDismissRequest = { showUpdate = false },
            title = { Text("发现新版本 ${info.tagName}") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "当前版本 v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (info.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(formatReleaseNotes(info.notes).take(900), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showUpdate = false
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure { onToast("打不开下载页") }
                }) { Text("去下载") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdate = false }) { Text("以后再说") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ThinDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 40.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    )
}

/** 把 Release 说明里的轻量 Markdown 变纯文本：去标题井号、列表短横改圆点、去粗体星号和代码标记、压掉多余空行 */
private fun formatReleaseNotes(raw: String): String {
    val lines = raw.replace("\r\n", "\n").split("\n").map { line ->
        val t = line.trimStart()
        when {
            t.startsWith("#") -> t.trimStart('#').trim()
            t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") -> "• " + t.drop(2).trim()
            else -> line.trimEnd()
        }
    }
    return lines.joinToString("\n")
        .replace("**", "")
        .replace("```", "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
