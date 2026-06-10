package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filter_rules")
data class FilterRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val targetName: String = "",
    val matchType: String = "CONTAINS", // "EXACT", "CONTAINS", "ANY"
    val keywords: String = "", // comma-separated keyword list
    val keywordBehavior: String = "NONE", // "BLOCK_IF_CONTAINS", "ALLOW_ONLY_IF_CONTAINS", "NONE"
    val priority: String = "ALLOW", // "BLOCK", "SILENCE", "ALLOW", "HIGH_PRIORITY"
    val isTimeRestricted: Boolean = false,
    val startTime: String = "00:00", // "HH:mm" in 24h
    val endTime: String = "23:59", // "HH:mm" in 24h
    val isActive: Boolean = true,
    val appPackage: String = "ALL"
)
