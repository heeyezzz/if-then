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
    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<Rule>)
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<ExecLog>)
}

data class RuleTriggerCount(val ruleId: Long, val cnt: Int)
