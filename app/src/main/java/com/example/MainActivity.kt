package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.FilterDatabase
import com.example.data.FilterRepository
import com.example.data.FilterRule
import com.example.data.NotificationLog
import com.example.ui.FilterViewModel
import com.example.ui.FilterViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        val msg = if (isGranted) "Notification channels initialized" else "Simulated alerts will not make sound"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = FilterDatabase.getDatabase(applicationContext)
        val repository = FilterRepository(db.filterDao())

        // Seed default rules on initial startup
        seedDefaultRulesIfNeeded(repository)

        setContent {
            MyApplicationTheme {
                val factory = FilterViewModelFactory(repository)
                val viewModel: FilterViewModel = viewModel(factory = factory)

                var hasNotificationAccess by remember { mutableStateOf(false) }
                val context = LocalContext.current

                // Check permission state reactively on launch and whenever app window regains focus
                LaunchedEffect(Unit) {
                    val enabled = isNotificationServiceEnabled(context)
                    hasNotificationAccess = enabled
                    if (enabled) {
                        forceRebindNotificationService(context)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            val enabled = isNotificationServiceEnabled(context)
                            hasNotificationAccess = enabled
                            if (enabled) {
                                forceRebindNotificationService(context)
                            }
                        }
                    })
                }

                NotificationFilterDashboard(
                    viewModel = viewModel,
                    isListenerEnabled = hasNotificationAccess,
                    onRequestPermission = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                        Toast.makeText(context, "Locate 'Notification Filter' in the list and enable it", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun seedDefaultRulesIfNeeded(repository: FilterRepository) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rulesSync = repository.getActiveRulesSync()
                if (rulesSync.isEmpty()) {
                    repository.insertRule(
                        FilterRule(
                            title = "Promo & Advertising Mute",
                            targetName = "", // global
                            keywords = "promo, crypto, offer, buy, deal, cash, invest, free",
                            keywordBehavior = "BLOCK_IF_CONTAINS",
                            priority = "BLOCK",
                            isTimeRestricted = false
                        )
                    )
                    repository.insertRule(
                        FilterRule(
                            title = "Urgent Whitelist",
                            targetName = "Dad",
                            matchType = "EXACT",
                            keywords = "",
                            keywordBehavior = "NONE",
                            priority = "HIGH_PRIORITY",
                            isTimeRestricted = false
                        )
                    )
                    repository.insertRule(
                        FilterRule(
                            title = "Work Hours Focus",
                            targetName = "General Chat",
                            matchType = "CONTAINS",
                            keywords = "",
                            keywordBehavior = "NONE",
                            priority = "SILENCE",
                            isTimeRestricted = true,
                            startTime = "09:00",
                            endTime = "17:00"
                        )
                    )
                }
            } catch (e: Exception) {
                // handle any DB thread exceptions peacefully
            }
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) {
                    return true
                }
            }
        }
        return false
    }

    private fun forceRebindNotificationService(context: Context) {
        try {
            val pm = context.packageManager
            val componentName = ComponentName(context, com.example.service.NotificationFilterService::class.java)
            // Disable component
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            // Re-enable component immediately
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d("MainActivity", "Toggled NotificationFilterService component for force rebind")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to force rebind by toggling component", e)
        }

        // Also call requestRebind explicitly on safe platforms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val componentName = ComponentName(context, com.example.service.NotificationFilterService::class.java)
                NotificationListenerService.requestRebind(componentName)
                Log.d("MainActivity", "NotificationListenerService.requestRebind called successfully")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed requestRebind call", e)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationFilterDashboard(
    viewModel: FilterViewModel,
    isListenerEnabled: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val blockedCount by viewModel.blockedCount.collectAsStateWithLifecycle()
    val allowedCount by viewModel.allowedCount.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) }
    var showAddRuleDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (activeTab == 0) {
                FloatingActionButton(
                    onClick = { showAddRuleDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary, // SophisticatedPrimary (#D0BCFF)
                    contentColor = MaterialTheme.colorScheme.onPrimary, // SophisticatedOnPrimary (#381E72)
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("add_rule_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add, 
                        contentDescription = "Add Rule",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Focus Filter",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("app_dashboard_title")
                        )
                        Text(
                            text = "$blockedCount notifications silenced today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isListenerEnabled) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFFEF6C00).copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isListenerEnabled) Color(0xFF81C784) else Color(0xFFFFB74D))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isListenerEnabled) "Active" else "Disabled",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isListenerEnabled) Color(0xFF81C784) else Color(0xFFFFB74D)
                                )
                            }
                        }

                        // Settings Icon Button Shortcut
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                                .clickable { onRequestPermission() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Warning Panel
                if (!isListenerEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("permission_panel")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Warning icon",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Listener Permission Required",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "In Android Settings, please search for 'Notification Listener Access' and enable 'Notification Filter' to analyze chats.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onRequestPermission,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .height(36.dp)
                                    .testTag("grant_permission_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Grant Access", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats row styled beautifully with Sophisticated Dark
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        title = "Interceptions",
                        value = blockedCount.toString(),
                        icon = Icons.Default.Close,
                        subtitle = "silenced alerts",
                        colorTheme = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1.0f)
                    )
                    StatCard(
                        title = "Passed Thru",
                        value = allowedCount.toString(),
                        icon = Icons.Default.Check,
                        subtitle = "important messages",
                        colorTheme = Color(0xFF66BB6A),
                        modifier = Modifier.weight(1.0f)
                    )
                    StatCard(
                        title = "Filter Rate",
                        value = if (blockedCount + allowedCount > 0) {
                            "${(blockedCount * 100) / (blockedCount + allowedCount)}%"
                        } else {
                            "0%"
                        },
                        icon = Icons.Default.Settings,
                        subtitle = "silence ratio",
                        colorTheme = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1.0f)
                    )
                }
            }

            // Tab Panels Menu Switcher with customized pill visual styling matching HTML
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = @Composable { _ -> } // Hidden standard bottom lines for customized rounded capsule design
            ) {
                // Tab 0: Rules
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    modifier = Modifier.testTag("tab_rules")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (activeTab == 0) MaterialTheme.colorScheme.secondary else Color.Transparent)
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = if (activeTab == 0) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Rules",
                            fontSize = 11.sp,
                            fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (activeTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Tab 1: Inbox
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    modifier = Modifier.testTag("tab_logs")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (activeTab == 1) MaterialTheme.colorScheme.secondary else Color.Transparent)
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = if (activeTab == 1) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Inbox",
                            fontSize = 11.sp,
                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (activeTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Tab 2: Simulator
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    modifier = Modifier.testTag("tab_simulator")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (activeTab == 2) MaterialTheme.colorScheme.secondary else Color.Transparent)
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (activeTab == 2) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Simulator",
                            fontSize = 11.sp,
                            fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Medium,
                            color = if (activeTab == 2) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Central Navigation Content Frame
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> RulesSection(
                        rules = rules,
                        onDeleteRule = { viewModel.deleteRule(it) },
                        onToggleRule = { rule, active -> viewModel.toggleRuleActive(rule, active) }
                    )
                    1 -> LogsSection(
                        logs = logs,
                        onClearLogs = { viewModel.clearLog() }
                    )
                    2 -> SimulatorSection(
                        onTrigger = { sender, text, app ->
                            viewModel.triggerSimulatedNotification(context, sender, text, app)
                        }
                    )
                }
            }
        }

        if (showAddRuleDialog) {
            AddRuleDialog(
                onDismiss = { showAddRuleDialog = false },
                onAddRule = { title, target, matchType, keywords, behavior, priority, restricted, start, end, appPkg ->
                    viewModel.addRule(
                        title, target, matchType, keywords, behavior, priority, restricted, start, end, appPkg
                    )
                }
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String,
    colorTheme: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorTheme,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colorTheme
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesSection(
    rules: List<FilterRule>,
    onDeleteRule: (FilterRule) -> Unit,
    onToggleRule: (FilterRule, Boolean) -> Unit
) {
    if (rules.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No custom rules yet",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap the '+' floating action button to create a custom notification filter rule.",
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            item {
                Text(
                    text = "ACTIVE FILTERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary, // #D0BCFF Lavender Accent
                    style = androidx.compose.ui.text.TextStyle(letterSpacing = 1.5.sp),
                    modifier = Modifier.padding(bottom = 4.dp, top = 4.dp)
                )
            }

            items(rules, key = { it.id }) { rule ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface, // Always Surface #2B2930
                    ),
                    shape = RoundedCornerShape(24.dp), // rounded-3xl corner shape
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline // #49454F border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (rule.isActive) 1.0f else 0.6f) // opacity-60 for disabled cards
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1.0f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when (rule.priority) {
                                                "BLOCK", "SILENCE" -> Icons.Default.Close
                                                "HIGH_PRIORITY" -> Icons.Default.Warning
                                                else -> Icons.Default.Check
                                            },
                                            contentDescription = "Priority icon",
                                            tint = when (rule.priority) {
                                                "BLOCK", "SILENCE" -> MaterialTheme.colorScheme.error
                                                "HIGH_PRIORITY" -> Color(0xFFFFB74D) // beautiful warning orange
                                                else -> Color(0xFF81C784) // priority high/normal green
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = rule.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = when (rule.priority) {
                                            "BLOCK", "SILENCE" -> "Action: Silenced & Logged"
                                            "HIGH_PRIORITY" -> "Action: High-Priority Whitelist Alert"
                                            else -> "Action: Allowed Standard"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = rule.isActive,
                                        onCheckedChange = { onToggleRule(rule, it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF381E72), // bg-[#381E72]
                                            checkedTrackColor = Color(0xFFD0BCFF), // bg-[#D0BCFF]
                                            uncheckedThumbColor = Color(0xFF938F99),
                                            uncheckedTrackColor = Color(0xFF49454F)
                                        ),
                                        modifier = Modifier
                                            .scale(0.85f)
                                            .testTag("rule_active_switch_${rule.id}")
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { onDeleteRule(rule) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("delete_rule_btn_${rule.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val appLabel = when (rule.appPackage) {
                                    "ALL", "" -> "ALL APPS"
                                    "com.facebook.orca" -> "MESSENGER"
                                    "com.whatsapp" -> "WHATSAPP"
                                    "org.telegram.messenger" -> "TELEGRAM"
                                    "com.instagram.android" -> "INSTAGRAM"
                                    "com.slack" -> "SLACK"
                                    "com.android.phone" -> "SMS ALERT"
                                    else -> rule.appPackage.split(".").lastOrNull()?.uppercase() ?: rule.appPackage.uppercase()
                                }
                                FilterBadgeChip(
                                    icon = Icons.Default.PlayArrow,
                                    label = "APP: $appLabel",
                                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )

                                if (rule.targetName.isNotEmpty()) {
                                    FilterBadgeChip(
                                        icon = Icons.Default.Person,
                                        label = "SENDER: ${rule.targetName.uppercase()} (${rule.matchType})"
                                    )
                                } else {
                                    FilterBadgeChip(
                                        icon = Icons.Default.Notifications,
                                        label = "SENDER: ALL CONTACTS"
                                    )
                                }

                                if (rule.keywords.isNotEmpty()) {
                                    FilterBadgeChip(
                                        icon = Icons.Default.Edit,
                                        label = "KEYWORD: ${rule.keywords.uppercase()}",
                                        badgeColor = MaterialTheme.colorScheme.secondary,
                                        labelColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                    FilterBadgeChip(
                                        icon = Icons.Default.Warning,
                                        label = "LOGIC: ${rule.keywordBehavior.replace("_", " ")}",
                                        badgeColor = if (rule.keywordBehavior == "BLOCK_IF_CONTAINS") MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f) else Color(0xFF1565C0).copy(alpha = 0.15f),
                                        labelColor = if (rule.keywordBehavior == "BLOCK_IF_CONTAINS") MaterialTheme.colorScheme.error else Color(0xFF90CAF9)
                                    )
                                }

                                if (rule.isTimeRestricted) {
                                    FilterBadgeChip(
                                        icon = Icons.Default.Warning,
                                        label = "HOURS: ${rule.startTime} - ${rule.endTime}",
                                        badgeColor = Color(0xFFE55100).copy(alpha = 0.15f),
                                        labelColor = Color(0xFFFFB74D)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun FilterBadgeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badgeColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(badgeColor)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = labelColor.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor
            )
        }
    }
}

@Composable
fun LogsSection(
    logs: List<NotificationLog>,
    onClearLogs: () -> Unit
) {
    if (logs.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No intercepted notifications yet",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "A record of all your incoming notification traffic will print here in real-time, categorized as Allowed or Silenced.",
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AUDIT INBOX FEED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary, // #D0BCFF Lavender Accent
                    style = androidx.compose.ui.text.TextStyle(letterSpacing = 1.5.sp)
                )

                TextButton(
                    onClick = onClearLogs,
                    modifier = Modifier.testTag("clear_logs_button"),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Clear Log",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear Stream", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.secondary)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = log.appName.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = log.sender,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (log.isBlocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f) else Color(0xFF2E7D32).copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (log.isBlocked) "Silenced" else "Passed",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (log.isBlocked) MaterialTheme.colorScheme.error else Color(0xFF81C784)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = log.message,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.appliedRuleName?.let { "Rule: $it" } ?: "No Rule Blocked It",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )

                                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                                Text(
                                    text = timeStr,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SimulatorSection(
    onTrigger: (sender: String, messageText: String, simulatedApp: String) -> Unit
) {
    var senderName by remember { mutableStateOf("") }
    var contentText by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf("WhatsApp") }
    var isExpanded by remember { mutableStateOf(false) }

    val appOptions = listOf("WhatsApp", "Telegram", "Slack", "Messenger", "SMS Alert")
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sandbox Simulator",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Because you do not have external messenger clients installed inside this remote emulator, use this custom sandbox to send native alerts and verify filter logic instantaneously!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "CRAFT A TEST NOTIFICATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    style = androidx.compose.ui.text.TextStyle(letterSpacing = 1.5.sp),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = isExpanded,
                    onExpandedChange = { isExpanded = !isExpanded },
                    modifier = Modifier.fillMaxWidth().testTag("app_loader")
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = selectedApp,
                        onValueChange = {},
                        label = { Text("App Origin") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isExpanded,
                        onDismissRequest = { isExpanded = false }
                    ) {
                        appOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedApp = option
                                    isExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text("Contact or Group Name") },
                    placeholder = { Text("e.g. Dad, Blocked Group, Crypto Alert") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sender_input_field"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    label = { Text("Message Body") },
                    placeholder = { Text("e.g. Please pick up or New crypto drop now!") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("message_input_field")
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Presets
                Text(
                    text = "PRESETS FOR AUTOFULL:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    style = androidx.compose.ui.text.TextStyle(letterSpacing = 1.0.sp),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            senderName = "Spam Bot Alert"
                            contentText = "Get 500% gains! Invest on the currency crypto now."
                            selectedApp = "WhatsApp"
                        },
                        modifier = Modifier.weight(1.0f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Spam Alert", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            senderName = "Dad"
                            contentText = "Please reply fast, this is urgent!"
                            selectedApp = "SMS Alert"
                        },
                        modifier = Modifier.weight(1.0f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Dad (Urgent)", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            senderName = "General Chat"
                            contentText = "Hey what are you guys up to this morning?"
                            selectedApp = "Telegram"
                        },
                        modifier = Modifier.weight(1.0f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("General Group", fontSize = 11.sp, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val sender = senderName.ifEmpty { "External Contact" }
                        val content = contentText.ifEmpty { "This is a quick notification simulation test alert." }
                        onTrigger(sender, content, selectedApp)
                        Toast.makeText(
                            context,
                            "Simulated Alert Posted as '$sender'",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("simulate_notif_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fire Simulated Alert", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(
    onDismiss: () -> Unit,
    onAddRule: (
        title: String,
        targetName: String,
        matchType: String,
        keywords: String,
        keywordBehavior: String,
        priority: String,
        isTimeRestricted: Boolean,
        startTime: String,
        endTime: String,
        appPackage: String
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var appPackage by remember { mutableStateOf("ALL") }
    var customAppPackage by remember { mutableStateOf("") }
    var targetName by remember { mutableStateOf("") }
    var matchType by remember { mutableStateOf("CONTAINS") } // EXACT, CONTAINS, ANY
    var keywords by remember { mutableStateOf("") }
    var keywordBehavior by remember { mutableStateOf("BLOCK_IF_CONTAINS") } // NONE, BLOCK_IF_CONTAINS, ALLOW_ONLY_IF_CONTAINS
    var priority by remember { mutableStateOf("BLOCK") } // BLOCK, ALLOW, HIGH_PRIORITY

    var isTimeRestricted by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("17:00") }

    var isAppPackageExpanded by remember { mutableStateOf(false) }
    var isMatchTypeExpanded by remember { mutableStateOf(false) }
    var isKeywordBehaviorExpanded by remember { mutableStateOf(false) }
    var isPriorityExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface, // #2B2930 Surface
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline // #49454F border
            ),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "Create Filter Rule",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Rule Title") },
                        placeholder = { Text("e.g. Silence Spam Keywords") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rule_title_input"),
                        singleLine = true
                    )
                }

                item {
                    ExposedDropdownMenuBox(
                        expanded = isAppPackageExpanded,
                        onExpandedChange = { isAppPackageExpanded = !isAppPackageExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_package_loader")
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = when (appPackage) {
                                "ALL" -> "All Applications"
                                "com.facebook.orca" -> "Facebook Messenger"
                                "com.whatsapp" -> "WhatsApp"
                                "org.telegram.messenger" -> "Telegram"
                                "com.instagram.android" -> "Instagram"
                                "CUSTOM" -> "Custom Package ID..."
                                else -> appPackage
                            },
                            onValueChange = {},
                            label = { Text("Filter Target App") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAppPackageExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = isAppPackageExpanded,
                            onDismissRequest = { isAppPackageExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Applications") },
                                onClick = {
                                    appPackage = "ALL"
                                    isAppPackageExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Facebook Messenger") },
                                onClick = {
                                    appPackage = "com.facebook.orca"
                                    isAppPackageExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("WhatsApp") },
                                onClick = {
                                    appPackage = "com.whatsapp"
                                    isAppPackageExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Telegram") },
                                onClick = {
                                    appPackage = "org.telegram.messenger"
                                    isAppPackageExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Instagram") },
                                onClick = {
                                    appPackage = "com.instagram.android"
                                    isAppPackageExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Custom Package ID...") },
                                onClick = {
                                    appPackage = "CUSTOM"
                                    isAppPackageExpanded = false
                                }
                            )
                        }
                    }
                }

                if (appPackage == "CUSTOM") {
                    item {
                        OutlinedTextField(
                            value = customAppPackage,
                            onValueChange = { customAppPackage = it },
                            label = { Text("Custom App Package (e.g. com.android.phone)") },
                            placeholder = { Text("com.example.app") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_app_pkg_input"),
                            singleLine = true
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = targetName,
                        onValueChange = { targetName = it },
                        label = { Text("Target Sender/Group Name(s)") },
                        placeholder = { Text("Group Name or comma-separated friends") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("rule_target_input"),
                        singleLine = true
                    )
                }

                if (targetName.isNotEmpty()) {
                    item {
                        ExposedDropdownMenuBox(
                            expanded = isMatchTypeExpanded,
                            onExpandedChange = { isMatchTypeExpanded = !isMatchTypeExpanded },
                            modifier = Modifier.fillMaxWidth().testTag("match_type_loader")
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = if (matchType == "CONTAINS") "Contains Match Strategy" else "Exact Match Strategy",
                                onValueChange = {},
                                label = { Text("Matching Strategy") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMatchTypeExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = isMatchTypeExpanded,
                                onDismissRequest = { isMatchTypeExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Contains Match Strategy") },
                                    onClick = {
                                        matchType = "CONTAINS"
                                        isMatchTypeExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Exact Match Strategy") },
                                    onClick = {
                                        matchType = "EXACT"
                                        isMatchTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text("Filter Keywords (Comma Separated)") },
                        placeholder = { Text("promo, invest, crypto, cash") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("rule_keywords_input")
                    )
                }

                if (keywords.isNotEmpty()) {
                    item {
                        ExposedDropdownMenuBox(
                            expanded = isKeywordBehaviorExpanded,
                            onExpandedChange = { isKeywordBehaviorExpanded = !isKeywordBehaviorExpanded },
                            modifier = Modifier.fillMaxWidth().testTag("keyword_behavior_loader")
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = when (keywordBehavior) {
                                    "BLOCK_IF_CONTAINS" -> "BLOCK if keywords match"
                                    "ALLOW_ONLY_IF_CONTAINS" -> "ALLOW ONLY if keywords match"
                                    else -> "NONE"
                                },
                                onValueChange = {},
                                label = { Text("Keyword Match Logic") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isKeywordBehaviorExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = isKeywordBehaviorExpanded,
                                onDismissRequest = { isKeywordBehaviorExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("BLOCK if keywords match") },
                                    onClick = {
                                        keywordBehavior = "BLOCK_IF_CONTAINS"
                                        isKeywordBehaviorExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("ALLOW ONLY if keywords match") },
                                    onClick = {
                                        keywordBehavior = "ALLOW_ONLY_IF_CONTAINS"
                                        isKeywordBehaviorExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    ExposedDropdownMenuBox(
                        expanded = isPriorityExpanded,
                        onExpandedChange = { isPriorityExpanded = !isPriorityExpanded },
                        modifier = Modifier.fillMaxWidth().testTag("priority_loader")
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = when (priority) {
                                "BLOCK" -> "BLOCK / Mute notification"
                                "ALLOW" -> "ALLOW / Normal Alert"
                                "HIGH_PRIORITY" -> "HIGH PRIORITY / Bypass Filters"
                                else -> "BLOCK / Mute notification"
                            },
                            onValueChange = {},
                            label = { Text("Rule Priority behavior") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isPriorityExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = isPriorityExpanded,
                            onDismissRequest = { isPriorityExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("BLOCK / Mute notification") },
                                onClick = {
                                    priority = "BLOCK"
                                    isPriorityExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("ALLOW / Normal Alert") },
                                onClick = {
                                    priority = "ALLOW"
                                    isPriorityExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("HIGH PRIORITY / Bypass Filters") },
                                onClick = {
                                    priority = "HIGH_PRIORITY"
                                    isPriorityExpanded = false
                                }
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Active Time Window",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Only apply filter during custom hours",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isTimeRestricted,
                            onCheckedChange = { isTimeRestricted = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF381E72),
                                checkedTrackColor = Color(0xFFD0BCFF),
                                uncheckedThumbColor = Color(0xFF938F99),
                                uncheckedTrackColor = Color(0xFF49454F)
                            ),
                            modifier = Modifier.testTag("time_restrict_switch")
                        )
                    }
                }

                if (isTimeRestricted) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = startTime,
                                onValueChange = { startTime = it },
                                label = { Text("Start HH:mm") },
                                placeholder = { Text("09:00") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .weight(1.0f)
                                    .testTag("start_time_input"),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = endTime,
                                onValueChange = { endTime = it },
                                label = { Text("End HH:mm") },
                                placeholder = { Text("17:00") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .weight(1.0f)
                                    .testTag("end_time_input"),
                                singleLine = true
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("cancel_rule_dialog_btn")
                        ) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                         Button(
                            onClick = {
                                if (title.trim().isEmpty()) {
                                    title = if (targetName.isNotEmpty()) {
                                        "Mute for $targetName"
                                    } else if (keywords.isNotEmpty()) {
                                        "Filter Keywords"
                                    } else {
                                        "Custom Filter Rule"
                                    }
                                }
                                val finalAppPkg = if (appPackage == "CUSTOM") {
                                    if (customAppPackage.trim().isEmpty()) "ALL" else customAppPackage.trim()
                                } else {
                                    appPackage
                                }
                                onAddRule(
                                    title,
                                    targetName,
                                    matchType,
                                    keywords,
                                    if (keywords.isNotEmpty()) keywordBehavior else "NONE",
                                    priority,
                                    isTimeRestricted,
                                    startTime,
                                    endTime,
                                    finalAppPkg
                                )
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("save_rule_dialog_btn")
                        ) {
                            Text("Save Rule", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
