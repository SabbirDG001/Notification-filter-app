# Notification Filter 🛡️
An advanced, highly-customizable Android notification interception, filtering, and silencing engine built with **Jetpack Compose**, **Kotlin**, and a localized high-performance **Room Database**.

---

## 📋 Table of Contents
1. [Overview](#-overview)
2. [Key Core Features](#-key-core-features)
3. [Technical Architecture](#-technical-architecture)
4. [Solutions Implemented in This Session](#-solutions-implemented-in-this-session)
5. [How to Deploy to Google Play Store](#%EF%B8%8F-how-to-deploy-to-google-play-store)
6. [Interactive Simulation Guide](#%EF%B8%8F-interactive-simulation-guide)

---

## 🔍 Overview
**Notification Filter** empowers Android users to regain control over their focus by intercepting distracting messenger notifications, spam chats, and low-priority social pings. 

Using fine-grained, localized rule structures, users can define whitelists (absolute priority), keyword blocklists, quiet hours, and app-specific rules. The app operates locally on-device via Android's `NotificationListenerService` subsystem, keeping user data perfectly private.

---

## ✨ Key Core Features

### 1. Robust Filtering Engine (`NotificationFilterService`)
- **App-Specific Targeting**: Map specific filter rules to targeted packages (e.g., WhatsApp, Facebook Messenger, Slack, Telegram, or custom Package IDs).
- **Keyword Processing Engine**: Silence notifications containing specific promotional patterns (e.g., `invest`, `bitcoin`, `promo`, `discount`) based on custom behaviors.
- **Dynamic Comma-Separated Matchers**: Allows combining multiple senders or groups into one single rule (e.g., `Notice Group, Promo Group, Spamer1`).
- **Flexible Match Modes**:
  - `EXACT` – Strict equality check on names, titles, or sub-texts.
  - `CONTAINS` – Look for partial names or substring sequences.
  - `ANY` – Blind-catch all incoming notifications for a given package or time window.

### 2. Built-in Notification Generator & Simulator
- Simulate incoming notifications from prominent platforms (**WhatsApp**, **Telegram**, **Slack**, **Messenger**, **SMS Alert**) directly from the UI.
- Rigorously test your newly registered rule sets instantly without needing external devices or separate apps.

### 3. Comprehensive Interception Logs
- Logs every intercepted item with real-time status: **ALLOWED BY DEFAULT**, **BLOCKED BY RULE**, **SILENCED BY TIME**, or **FORCE ALLOWED (WHITELIST)**.
- Explains the exact logic path used during evaluation for debug clarity.

---

## 🛠️ Technical Architecture

```
com.example
├── MainActivity.kt               # Jetpack Compose UI (Dashboard, Logs, Simulator)
├── data
│   ├── FilterRule.kt             # Room Database Entity representing filter criteria
│   ├── FilterDao.kt              # Room Data Access Object for local rule persistence
│   ├── FilterDatabase.kt         # Migratable local SQLite wrapper
│   ├── FilterRepository.kt       # Clean repository layer mapping flows to the VM
│   └── NotificationLog.kt        # Intercepted metadata logs for analysis
├── service
│   └── NotificationFilterService # System service extending NotificationListenerService
└── ui
    └── FilterViewModel.kt        # State Management, simulated trigger helpers & actions
```

---

## 🚀 Solutions Implemented in This Session

### 1. The "Closed-App / Service Interruption" Bug Fix
* **The Issue**: On modern Android versions, swiping the app away from the Recents menu can cause the operating system to aggressively tear down background listener bindings, resulting in missed blocks.
* **The Solution**: 
  - Added a reactive **Self-Recovery Rebind Helper** inside `MainActivity`. 
  - Whenever the app is launched or regains user focus, it checks listener status and calls:
    ```kotlin
    NotificationListenerService.requestRebind(componentName)
    ```
  - It also triggers a safe state transition cycle (Disable ➡️ Enable component toggling) via `PackageManager` to force Android's system binding manager to reliably re-provision the listener.

### 2. Group Chat "Missed Matcher" Bug Fix
* **The Issue**: Group chats on Telegram, WhatsApp, and Messenger do not always place the active group name under the standard `EXTRA_TITLE` key.
* **The Solution**: Upgraded the matching engine to extract and inspect multiple critical notification payload keys:
  - `Notification.EXTRA_TITLE` (Sender)
  - `Notification.EXTRA_CONVERSATION_TITLE` (Group/Chatroom name)
  - `Notification.EXTRA_SUB_TEXT` (Secondary label / Thread context)
* This ensures that any wildcard filter containing group titles gets captured perfectly.

### 3. Interactive Multi-Target / Comma-Separated Matching Engine
* Optimized `NotificationFilterService` to parse comma-separated wildcard rules, gracefully executing matching logic against multiple contacts or categories in real-time.

---

## 🛍️ How to Deploy to Google Play Store

Because this app utilizes a high-privilege Android permission (`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`), submitting it to the Play Store requires fulfilling several strict Google Play Developer requirements:

### Step 1: Prepare Play Console Account
1. Open a developer account on the **[Google Play Console](https://play.google.com/console)**.
2. Complete developer verification steps (D-U-N-S or ID/Address Verification).

### Step 2: Configure Production Build Settings
Before signing your release bundle, make sure `app/build.gradle.kts` is properly populated:
* Change `applicationId` to a unique reverse-domain string (accomplished automatically in our platform settings).
* Ensure `minifyEnabled` is active to optimize file-size and obfuscate class files.

### Step 3: Google Play Policy & Prominent Disclosure
Android's **Notification Access** is classified as a sensitive permission. You must:
1. Provide a **Prominent Disclosure Narrative** in-app explaining precisely *why* the BIND_NOTIFICATION_LISTENER_SERVICE is required.
2. Host an external **Privacy Policy** document detailing that all intercepted notification content remains strictly local to the device and is never uploaded.
3. Prepare a visual video walkthrough demonstration for Google Quality review showing the core filter mechanics.

---

## 🕹️ Interactive Simulation Guide

To see the system in active, reliable action:
1. Go to the **Simulator** tab in the App.
2. Create a rule (e.g., **App**: `WhatsApp`, **Type**: `CONTAINS`, **Rule Target**: `Family Group`, **Behavior**: `Block`).
3. Press **Trigger Notification** with a simulated message under `Family Group`.
4. Check the **Logs** tab: you'll see a real-time event entry verifying the intercept:
   > 🔴 *Blocked by Rule: Family Group Block - WhatsApp*

---

*Generated in Google AI Studio • Designed with Elegant Minimalist Compose Aesthetics.*
