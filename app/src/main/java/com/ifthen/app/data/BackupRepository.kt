package com.ifthen.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
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
        db.execLogDao().deleteAll()
        db.ruleDao().deleteAll()
        db.ruleDao().insertAll(payload.rules)
        db.execLogDao().insertAll(payload.logs)
        return payload.rules.size
    }
}
