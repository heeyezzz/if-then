package com.ifthen.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class BackupPayload(
    val version: Int = 7,
    val exportedAt: Long = System.currentTimeMillis(),
    val folders: List<Folder> = emptyList(),
    val chains: List<Chain> = emptyList(),
    val rules: List<Rule>,
    val logs: List<ExecLog>
)

/**
 * 只备份业务数据；API key 存在 DataStore，不进导出文件。
 * v7 起带 chains：链是真实体，不再靠 nextRuleId 指针重建。
 */
class BackupRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    suspend fun exportTo(uri: Uri): Int {
        val db = AppDatabase.get(context)
        val payload = BackupPayload(
            folders = db.folderDao().observeAll().first(),
            chains = db.chainDao().observeAll().first(),
            rules = db.ruleDao().observeAll().first(),
            logs = db.execLogDao().observeSince(0L).first()
        )
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.encodeToString(BackupPayload.serializer(), payload).toByteArray())
        } ?: error("无法写入文件")
        return payload.rules.size
    }

    suspend fun importFrom(uri: Uri): Int {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: error("无法读取文件")
        val tree = json.parseToJsonElement(text)
        if (tree !is JsonObject || tree["chains"] == null) {
            error("旧版备份不受支持：链已升级为实体，请用升级后的版本重新导出一份再恢复")
        }
        val payload = json.decodeFromJsonElement(BackupPayload.serializer(), tree)
        val chainIds = payload.chains.mapTo(HashSet()) { it.id }
        // 悬空的链引用退回散预案，不让一条坏链引用把整份导入带崩
        val rules = payload.rules.map { r ->
            if (r.chainId != null && r.chainId !in chainIds) r.copy(chainId = null, stepIndex = null)
            else r
        }
        val db = AppDatabase.get(context)
        db.execLogDao().deleteAll()
        db.ruleDao().deleteAll()
        db.folderDao().deleteAll()
        db.chainDao().deleteAll()
        db.chainDao().insertAll(payload.chains)
        db.folderDao().insertAll(payload.folders)
        db.ruleDao().insertAll(rules)
        db.execLogDao().insertAll(payload.logs)
        return rules.size
    }
}
