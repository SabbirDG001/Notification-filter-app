package com.example.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.FilterRepository
import com.example.data.FilterRule
import com.example.data.NotificationLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FilterViewModel(private val repository: FilterRepository) : ViewModel() {

    val rules: StateFlow<List<FilterRule>> = repository.allRules
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val logs: StateFlow<List<NotificationLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val blockedCount: StateFlow<Int> = repository.blockedCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val allowedCount: StateFlow<Int> = repository.allowedCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun addRule(
        title: String,
        targetName: String,
        matchType: String,
        keywords: String,
        keywordBehavior: String,
        priority: String,
        isTimeRestricted: Boolean,
        startTime: String,
        endTime: String,
        appPackage: String = "ALL"
    ) {
        viewModelScope.launch {
            val rule = FilterRule(
                title = title,
                targetName = targetName,
                matchType = matchType,
                keywords = keywords,
                keywordBehavior = keywordBehavior,
                priority = priority,
                isTimeRestricted = isTimeRestricted,
                startTime = startTime,
                endTime = endTime,
                isActive = true,
                appPackage = appPackage
            )
            repository.insertRule(rule)
        }
    }

    fun deleteRule(rule: FilterRule) {
        viewModelScope.launch {
            repository.deleteRule(rule)
        }
    }

    fun toggleRuleActive(rule: FilterRule, isActive: Boolean) {
        viewModelScope.launch {
            repository.toggleRuleActive(rule.id, isActive)
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    /**
     * Helper to simulate an incoming message notification from external apps (e.g., Whatsapp, Telegram)
     * so that the user handles simulated incoming traffic in the emulator and tests their rules easily.
     */
    fun triggerSimulatedNotification(
        context: Context,
        sender: String,
        content: String,
        simulatedAppName: String
    ) {
        val channelId = "filter_simulated_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Simulated Chats (Notifilter Test)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Simulates incoming messenger notifications for rules testing."
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Map simulated app to realistic package names for evaluation
        val simulatedPackageName = when (simulatedAppName.lowercase()) {
            "whatsapp" -> "com.whatsapp"
            "telegram" -> "org.telegram.messenger"
            "slack" -> "com.slack"
            "messenger" -> "com.facebook.orca"
            "sms alert" -> "com.android.phone"
            else -> "com.example.mock"
        }

        // Put necessary extras matching native messenger keys to be caught by our NotificationListenerService
        val extras = Bundle().apply {
            putCharSequence(android.app.Notification.EXTRA_TITLE, sender)
            putCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE, sender)
            putCharSequence(android.app.Notification.EXTRA_SUB_TEXT, sender)
            putCharSequence(android.app.Notification.EXTRA_TEXT, content)
            putCharSequence(android.app.Notification.EXTRA_BIG_TEXT, content)
            // Custom indicator so the notification listener knows it's a test to intercept
            putBoolean("is_simulated_notification", true)
            putString("simulated_package_name", simulatedPackageName)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(sender)
            .setContentText(content)
            .setSubText(simulatedAppName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addExtras(extras)
            .setAutoCancel(true)

        // Generate dynamic notification ID to stack them if needed
        val notificationId = (System.currentTimeMillis() % 100000).toInt() + 1000
        notificationManager.notify(notificationId, builder.build())
    }
}

class FilterViewModelFactory(private val repository: FilterRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FilterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FilterViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
