package com.example.data

import kotlinx.coroutines.flow.Flow

class FilterRepository(private val filterDao: FilterDao) {
    val allRules: Flow<List<FilterRule>> = filterDao.getAllRules()
    val allLogs: Flow<List<NotificationLog>> = filterDao.getAllLogs()
    val blockedCount: Flow<Int> = filterDao.getBlockedCount()
    val allowedCount: Flow<Int> = filterDao.getAllowedCount()

    suspend fun getActiveRulesSync(): List<FilterRule> {
        return filterDao.getActiveRulesSync()
    }

    suspend fun insertRule(rule: FilterRule) {
        filterDao.insertRule(rule)
    }

    suspend fun deleteRule(rule: FilterRule) {
        filterDao.deleteRule(rule)
    }

    suspend fun toggleRuleActive(ruleId: Int, isActive: Boolean) {
        filterDao.toggleRuleActive(ruleId, isActive)
    }

    suspend fun insertLog(log: NotificationLog) {
        filterDao.insertLog(log)
    }

    suspend fun clearLogs() {
        filterDao.clearAllLogs()
    }
}
