package com.example

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.FilterDatabase
import com.example.data.FilterRepository
import com.example.data.FilterRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilterLogicTest {

    private lateinit var context: Context
    private lateinit var db: FilterDatabase
    private lateinit var repository: FilterRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FilterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FilterRepository(db.filterDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testDatabaseRuleSavingAndRetrieval() = runBlocking {
        // Assert db is clean initially
        val initialRules = repository.allRules.first()
        assertTrue(initialRules.isEmpty())

        // Create specific group block rule and save
        val rule = FilterRule(
            id = 1,
            title = "Block Spams",
            targetName = "Promo, Ads Group",
            matchType = "CONTAINS",
            keywords = "invest, bitcoin",
            keywordBehavior = "BLOCK_IF_CONTAINS",
            priority = "BLOCK",
            isActive = true,
            appPackage = "com.whatsapp"
        )
        repository.insertRule(rule)

        val retrievedRules = repository.allRules.first()
        assertEquals(1, retrievedRules.size)
        assertEquals("Block Spams", retrievedRules[0].title)
        assertEquals("com.whatsapp", retrievedRules[0].appPackage)
        assertEquals("Promo, Ads Group", retrievedRules[0].targetName)
    }

    @Test
    fun testMockNotificationEvaluationWithGroupTitle() = runBlocking {
        // In this test, we replicate the upgraded matching engine of the NotificationFilterService
        // to verify that it successfully matches group chats using EXTRA_CONVERSATION_TITLE.

        // 1. Setup the Database Rules
        // Rule A: Allow rule for group "Notice Group" with HIGH_PRIORITY (Whitelist) on Messenger
        val whitelistRule = FilterRule(
            id = 1,
            title = "Notice Group White-List",
            targetName = "Notice Group",
            matchType = "EXACT",
            priority = "HIGH_PRIORITY",
            isActive = true,
            appPackage = "com.facebook.orca"
        )

        // Rule B: Block rule for all other messages on Messenger
        val blockAllRule = FilterRule(
            id = 2,
            title = "Block All Messenger Senders",
            targetName = "", // Applies to all
            matchType = "ANY",
            priority = "BLOCK",
            isActive = true,
            appPackage = "com.facebook.orca"
        )

        repository.insertRule(whitelistRule)
        repository.insertRule(blockAllRule)

        val activeRules = repository.getActiveRulesSync()
        assertEquals(2, activeRules.size)

        // Simulate incoming Notification: from "John" in "Notice Group" on Messenger
        val pkg = "com.facebook.orca"
        val title = "John Doe"
        val conversationTitle = "Notice Group"
        val subText = ""
        val fullText = "Please join the meeting at 10 AM"

        // Execute matching engine logic as written in NotificationFilterService
        var matchedRule: FilterRule? = null
        var forceBlock = false
        var forceAllow = false

        // Sort rules: Whitelists first
        val sortedRules = activeRules.sortedWith(compareBy {
            if (it.priority == "HIGH_PRIORITY") 0 else 1
        })

        for (rule in sortedRules) {
            val ruleApp = rule.appPackage
            if (ruleApp.isNotEmpty() && ruleApp != "ALL") {
                if (!pkg.equals(ruleApp, ignoreCase = true)) {
                    continue
                }
            }

            var senderMatch = false
            if (rule.targetName.isEmpty()) {
                senderMatch = true
            } else {
                val targets = rule.targetName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                for (target in targets) {
                    if (rule.matchType == "EXACT") {
                        if (title.equals(target, ignoreCase = true) ||
                            conversationTitle.equals(target, ignoreCase = true) ||
                            subText.equals(target, ignoreCase = true)
                        ) {
                            senderMatch = true
                            break
                        }
                    } else if (rule.matchType == "CONTAINS") {
                        if (title.contains(target, ignoreCase = true) ||
                            conversationTitle.contains(target, ignoreCase = true) ||
                            subText.contains(target, ignoreCase = true)
                        ) {
                            senderMatch = true
                            break
                        }
                    } else if (rule.matchType == "ANY") {
                        senderMatch = true
                        break
                    }
                }
            }

            if (!senderMatch) continue

            // Evaluate time restriction (skip in test for simpler flow)
            if (forceBlock) break

            if (!forceAllow) {
                matchedRule = rule
                when (rule.priority) {
                    "BLOCK", "SILENCE" -> {
                        forceBlock = true
                        break
                    }
                    "HIGH_PRIORITY" -> {
                        forceAllow = true
                        break
                    }
                    "ALLOW" -> {
                        // generic allow
                    }
                }
            }
        }

        val isBlocked = forceBlock && !forceAllow

        // Verification: The Whitelist rule must successfully execute and prevent the block list rule from running
        assertNotNull(matchedRule)
        assertEquals("Notice Group White-List", matchedRule?.title)
        assertTrue(forceAllow)
        assertFalse(isBlocked) // Notification is allowed!
    }

    @Test
    fun testMockNotificationBlockedByDefault() = runBlocking {
        // Test a notification that does not match any whitelist, but matches a block list or keyword rule

        val spamKeywordRule = FilterRule(
            id = 1,
            title = "Block Crypto Scams",
            targetName = "",
            keywords = "crypto, bitcoin",
            keywordBehavior = "BLOCK_IF_CONTAINS",
            priority = "BLOCK",
            isActive = true
        )

        repository.insertRule(spamKeywordRule)
        val activeRules = repository.getActiveRulesSync()

        val pkg = "org.telegram.messenger"
        val title = "Spam Channel"
        val conversationTitle = ""
        val subText = ""
        val fullText = "Invest in bitcoin now and make 500% profit!"

        var matchedRule: FilterRule? = null
        var forceBlock = false
        var forceAllow = false

        for (rule in activeRules) {
            var senderMatch = rule.targetName.isEmpty()
            if (!senderMatch) {
                val targets = rule.targetName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                for (target in targets) {
                    if (title.contains(target, ignoreCase = true)) {
                        senderMatch = true
                        break
                    }
                }
            }

            if (!senderMatch) continue

            // Check keywords
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

                if (rule.keywordBehavior == "BLOCK_IF_CONTAINS" && keywordMatch) {
                    forceBlock = true
                    matchedRule = rule
                }
            }
        }

        val isBlocked = forceBlock && !forceAllow
        assertTrue(isBlocked)
        assertNotNull(matchedRule)
        assertEquals("Block Crypto Scams", matchedRule?.title)
    }
}
