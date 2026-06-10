package com.example.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.FilterDatabase
import com.example.data.FilterRepository
import com.example.data.FilterRule
import com.example.data.NotificationLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationFilterService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: FilterRepository

    companion object {
        private const val TAG = "NotifilterService"
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        val db = FilterDatabase.getDatabase(applicationContext)
        repository = FilterRepository(db.filterDao())
        Log.d(TAG, "Notification Filter Service Created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceRunning = true
        Log.d(TAG, "Notification Filter Service Connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceRunning = false
        Log.d(TAG, "Notification Filter Service Disconnected")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val notif = sbn.notification ?: return
        val extras = notif.extras ?: Bundle.EMPTY
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val fullText = if (text.isNotEmpty()) text else bigText

        var pkg = sbn.packageName ?: ""
        val isSimulated = extras.getBoolean("is_simulated_notification", false)
        if (isSimulated && extras.containsKey("simulated_package_name")) {
            pkg = extras.getString("simulated_package_name") ?: pkg
        }

        // Discard null notification content
        if (title.isEmpty() && fullText.isEmpty() && conversationTitle.isEmpty()) return

        // Skip our own non-simulated notification to prevent infinite loops, but capture simulated ones
        if (sbn.packageName == packageName && !isSimulated) {
            return
        }

        val pm = applicationContext.packageManager
        val appLabel = try {
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            when (pkg) {
                "com.facebook.orca" -> "Messenger"
                "com.whatsapp" -> "WhatsApp"
                "org.telegram.messenger" -> "Telegram"
                "com.slack" -> "Slack"
                "com.android.phone" -> "SMS Alert"
                else -> pkg.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg
            }
        }

        serviceScope.launch {
            try {
                val activeRules = repository.getActiveRulesSync()
                var matchedRule: FilterRule? = null
                var forceBlock = false
                var forceAllow = false
                var ruleDetails = "Allowed by Default"

                // Sort rules: Whitelists (HIGH_PRIORITY) first so they have absolute priority over default blocks
                val sortedRules = activeRules.sortedWith(compareBy {
                    if (it.priority == "HIGH_PRIORITY") 0 else 1
                })

                for (rule in sortedRules) {
                    // Match Target App Package (if specified)
                    val ruleApp = rule.appPackage
                    if (ruleApp.isNotEmpty() && ruleApp != "ALL") {
                        if (!pkg.equals(ruleApp, ignoreCase = true)) {
                            // Rule is app-specific and does not match this app, skip it
                            continue
                        }
                    }

                    var senderMatch = false

                    // Match Target Name/Group (Sender Name) - Supports comma-separated titles
                    if (rule.targetName.isEmpty()) {
                        senderMatch = true // Applies to all senders unless restricted
                    } else {
                        val targets = rule.targetName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        for (target in targets) {
                            when (rule.matchType) {
                                "EXACT" -> {
                                    if (title.equals(target, ignoreCase = true) ||
                                        conversationTitle.equals(target, ignoreCase = true) ||
                                        subText.equals(target, ignoreCase = true)
                                    ) {
                                        senderMatch = true
                                        break
                                    }
                                }
                                "CONTAINS" -> {
                                    if (title.contains(target, ignoreCase = true) ||
                                        conversationTitle.contains(target, ignoreCase = true) ||
                                        subText.contains(target, ignoreCase = true)
                                    ) {
                                        senderMatch = true
                                        break
                                    }
                                }
                                "ANY" -> {
                                    senderMatch = true
                                    break
                                }
                            }
                        }
                    }

                    if (!senderMatch) continue

                    // Check keywords if provided
                    var keywordMatch = false
                    if (rule.keywords.isNotEmpty()) {
                        val keywordsList = rule.keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        for (kw in keywordsList) {
                            if (fullText.contains(kw, ignoreCase = true) ||
                                title.contains(kw, ignoreCase = true) ||
                                conversationTitle.contains(kw, ignoreCase = true) ||
                                subText.contains(kw, ignoreCase = true)
                            ) {
                                keywordMatch = true
                                break
                            }
                        }

                        when (rule.keywordBehavior) {
                            "BLOCK_IF_CONTAINS" -> {
                                if (keywordMatch) {
                                    forceBlock = true
                                    matchedRule = rule
                                    ruleDetails = "Blocked by keyword match: '${rule.keywords}'"
                                }
                            }
                            "ALLOW_ONLY_IF_CONTAINS" -> {
                                if (!keywordMatch) {
                                    forceBlock = true
                                    matchedRule = rule
                                    ruleDetails = "Blocked: Missing required keyword in '${rule.keywords}'"
                                } else {
                                    forceAllow = true
                                    matchedRule = rule
                                    ruleDetails = "Allowed: Contains required keyword in '${rule.keywords}'"
                                }
                            }
                        }
                    }

                    // Check Time Constraints
                    if (rule.isTimeRestricted) {
                        val currentTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        if (!isCurrentTimeInRange(currentTimeStr, rule.startTime, rule.endTime)) {
                            // Time filter is not satisfied right now, skip this rule
                            continue
                        }
                    }

                    if (forceBlock) break

                    // Default Priority actions if keywords did not trigger a hard block
                    if (!forceAllow) {
                        matchedRule = rule
                        when (rule.priority) {
                            "BLOCK", "SILENCE" -> {
                                forceBlock = true
                                ruleDetails = "Silenced by Rule: ${rule.title}"
                                break
                            }
                            "HIGH_PRIORITY" -> {
                                forceAllow = true
                                ruleDetails = "Priority Allowed by Rule: ${rule.title}"
                                break
                            }
                            "ALLOW" -> {
                                ruleDetails = "Allowed by Rule: ${rule.title}"
                            }
                        }
                    }
                }

                val isBlocked = forceBlock && !forceAllow

                if (isBlocked) {
                    // Try to silence the notification by cancelling it on active channels
                    cancelNotification(sbn.key)
                    Log.d(TAG, "Notification intercepted & cancelled: $title - $fullText")
                } else {
                    Log.d(TAG, "Notification allowed to pass: $title - $fullText")
                }

                // Log entry
                val log = NotificationLog(
                    appName = appLabel,
                    packageName = pkg,
                    sender = title,
                    message = fullText,
                    isBlocked = isBlocked,
                    appliedRuleName = matchedRule?.title ?: if (isBlocked) "Default Filter" else "Default System",
                    priority = matchedRule?.priority ?: "ALLOW"
                )
                repository.insertLog(log)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification posted", e)
            }
        }
    }

    private fun isCurrentTimeInRange(current: String, start: String, end: String): Boolean {
        return try {
            val currMin = timeToMinutes(current)
            val startMin = timeToMinutes(start)
            val endMin = timeToMinutes(end)
            if (startMin <= endMin) {
                currMin in startMin..endMin
            } else {
                currMin >= startMin || currMin <= endMin
            }
        } catch (e: Exception) {
            true
        }
    }

    private fun timeToMinutes(time: String): Int {
        val parts = time.split(":")
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        return h * 60 + m
    }
}
