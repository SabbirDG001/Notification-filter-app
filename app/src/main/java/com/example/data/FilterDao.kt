package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterDao {
    // --- Rules Queries ---
    @Query("SELECT * FROM filter_rules ORDER BY id DESC")
    fun getAllRules(): Flow<List<FilterRule>>

    @Query("SELECT * FROM filter_rules WHERE isActive = 1")
    suspend fun getActiveRulesSync(): List<FilterRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: FilterRule)

    @Delete
    suspend fun deleteRule(rule: FilterRule)

    @Query("UPDATE filter_rules SET isActive = :isActive WHERE id = :ruleId")
    suspend fun toggleRuleActive(ruleId: Int, isActive: Boolean)

    // --- Logs Queries ---
    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<NotificationLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: NotificationLog)

    @Query("DELETE FROM notification_logs")
    suspend fun clearAllLogs()

    @Query("SELECT COUNT(*) FROM notification_logs WHERE isBlocked = 1")
    fun getBlockedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_logs WHERE isBlocked = 0")
    fun getAllowedCount(): Flow<Int>
}
