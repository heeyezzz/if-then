package com.ifthen.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupPayload(
    val version: Int = 6,
    val exportedAt: Long = System.currentTimeMillis(),
    val folders: List<Folder> = emptyList(),
    val rules: List<Rule>,
    val logs: List<ExecLog>
)

/**
 * 只备份业务数据；API key 存在 DataStore，不进导出文件。
 */
class BackupRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun exportTo(uri: Uri): Int {
        val db = AppDatabase.get(context)
        val payload = BackupPayload(
            folders = db.folderDao().observeAll().first(),
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
        val payload = json.decodeFromString(BackupPayload.serializer(), text)
        val db = AppDatabase.get(context)
        // 导入的链条指针可能悬空、分叉或成环，按 Chain 的规则重建
        val knownStatus = RuleStatus.entries.map { it.name }.toSet()
        val knownKind = RuleKind.entries.map { it.name }.toSet()
        val normalized = payload.rules.map { r ->
            r.copy(
                status = if (r.status in knownStatus) r.status else RuleStatus.ACTIVE.name,
                kind = if (r.kind in knownKind) r.kind else RuleKind.REPEATING.name
            )
        }
        val next = Chain.sanitizedNext(normalized.filterNot { it.isOneOff })
        val rules = normalized.map { it.copy(nextRuleId = if (it.isOneOff) null else next[it.id]) }
        db.execLogDao().deleteAll()
        db.ruleDao().deleteAll()
        db.folderDao().deleteAll()
        db.folderDao().insertAll(payload.folders)
        db.ruleDao().insertAll(rules)
        db.execLogDao().insertAll(payload.logs)
        return payload.rules.size
    }
}
