package com.ifthen.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.ifthen.app.BuildConfig
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("设置", style = MaterialTheme.typography.headlineMedium)

            Text("AI 接口（BYOK，OpenAI 兼容协议）", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                supportingText = { Text("如 https://api.deepseek.com/v1") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = model, onValueChange = { model = it },
                label = { Text("模型名") },
                supportingText = { Text("如 deepseek-flash") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Button(
                onClick = {
                    vm.saveApiSettings(
                        com.ifthen.app.data.ApiSettings(baseUrl.trim(), apiKey.trim(), model.trim())
                    )
                    onToast("已保存")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存接口配置") }
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
                modifier = Modifier.fillMaxWidth()
            ) {
                if (testing) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp
                    )
                    Text("  测试连接中…")
                } else {
                    Text("测试连接")
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("数据", style = MaterialTheme.typography.titleMedium)
            Text(
                "导出不包含 API Key。导入会覆盖当前全部数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { exportLauncher.launch("ifthen-backup.json") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("导出 JSON 备份") }
            Button(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("从 JSON 恢复") }

            Spacer(Modifier.height(12.dp))
            Text("关于", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("当前版本")
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                enabled = !checking,
                onClick = {
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
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("  检查中…")
                } else {
                    Text("检查更新")
                }
            }
            Text(
                "从 GitHub Releases 获取最新版本",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(60.dp))
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
                        Text(info.notes.trim().take(600), style = MaterialTheme.typography.bodyMedium)
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
