package com.ifthen.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<Rule>>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getById(id: Long): Rule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: Rule): Long

    @Update
    suspend fun update(rule: Rule)

    @Delete
    suspend fun delete(rule: Rule)

    @Query("DELETE FROM rules")
    suspend fun deleteAll()

    @Query("SELECT * FROM rules WHERE folderId IN (:folderIds)")
    suspend fun rulesInFolders(folderIds: List<Long>): List<Rule>

    @Query("DELETE FROM rules WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<Rule>)

    @Query("UPDATE rules SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM rules WHERE folderId IS :folderId")
    suspend fun maxSortOrder(folderId: Long?): Int

    @Query("SELECT * FROM rules WHERE nextRuleId = :id LIMIT 1")
    suspend fun predecessorOf(id: Long): Rule?

    @Query("UPDATE rules SET nextRuleId = :next WHERE id = :id")
    suspend fun updateNext(id: Long, next: Long?)
}

@Dao
interface ExecLogDao {
    @Query("SELECT * FROM exec_logs WHERE createdAt >= :since ORDER BY createdAt DESC")
    fun observeSince(since: Long): Flow<List<ExecLog>>

    @Query("SELECT * FROM exec_logs WHERE ruleId = :ruleId ORDER BY createdAt DESC LIMIT 100")
    suspend fun recentForRule(ruleId: Long): List<ExecLog>

    @Query("SELECT ruleId, COUNT(*) AS cnt FROM exec_logs WHERE createdAt >= :since GROUP BY ruleId")
    suspend fun triggerCounts(since: Long): List<RuleTriggerCount>

    @Query("SELECT COALESCE(MAX(createdAt), -1) FROM exec_logs WHERE ruleId = :ruleId")
    suspend fun lastTriggerAt(ruleId: Long): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ExecLog): Long

    @Update
    suspend fun update(log: ExecLog)

    @Delete
    suspend fun delete(log: ExecLog)

    @Query("DELETE FROM exec_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM exec_logs WHERE ruleId IN (:ruleIds)")
    suspend fun deleteForRules(ruleIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<ExecLog>)
}

data class RuleTriggerCount(val ruleId: Long, val cnt: Int)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<Folder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: Folder): Long

    @Update
    suspend fun update(folder: Folder)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<Folder>)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM folders WHERE parentId IS :parentId")
    suspend fun maxSortOrder(parentId: Long?): Int
}
