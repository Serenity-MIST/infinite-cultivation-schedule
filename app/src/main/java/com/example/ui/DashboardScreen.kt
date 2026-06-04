package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DbTask
import com.example.data.DbStats
import com.example.ui.theme.*
import com.example.viewmodel.CompanionViewModel
import com.example.viewmodel.DashboardState
import com.example.viewmodel.AlertItem

@Composable
fun DashboardScreen(
    viewModel: CompanionViewModel,
    modifier: Modifier = Modifier
) {
    val dashboardState by viewModel.dashboardState.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val highlightedTask by viewModel.randomTaskHighlight.collectAsState()
    val stats by viewModel.cultivationStats.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanningState by viewModel.scanningState.collectAsState()
    val activeAlerts by viewModel.activeAlerts.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.processRealScreenshot(context, uri)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnyxBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar
            HeaderSection()

            // Content Area
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("dashboard_scroll_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Highly visible active notifications & banners
                if (activeAlerts.isNotEmpty()) {
                    item {
                        ActiveAlertsBanners(
                            alerts = activeAlerts,
                            onChimeClick = { viewModel.triggerChime() }
                        )
                    }
                }

                // Diagnostics panel
                item {
                    TimeSimulationConsole(
                        timeLabel = viewModel.simulatedTimeText,
                        onSimulateTime = { h, m -> viewModel.setSimulatedTime(h, m) },
                        onResetTime = { viewModel.resetSimulatedTime() }
                    )
                }

                // Alternating Cycles
                item {
                    SectionHeader(title = "Alternating Cycle Tracker", icon = Icons.Default.Refresh)
                }
                item {
                    BiWeeklyCycleCard(dashboardState = dashboardState)
                }

                // Stat growth calculator section
                item {
                    SectionHeader(title = "Screenshot Stat Scanner", icon = Icons.Default.Add)
                }
                item {
                    StatScannerSection(
                        stats = stats,
                        isScanning = isScanning,
                        scanningState = scanningState,
                        onScanClick = { preset -> viewModel.processUploadedScreenshot(preset) },
                        onSelectRealImage = { imagePickerLauncher.launch("image/*") },
                        onManualSubmit = { str, agl, phy, intel, elem ->
                            viewModel.submitManualStats(str, agl, phy, intel, elem)
                        },
                        onResetBaseline = { viewModel.forceResetBaseline() }
                    )
                }

                // Weekend Realms & Shops
                item {
                    SectionHeader(title = "Realm & Merchant Schedules", icon = Icons.Default.Info)
                }
                item {
                    RealmShopGrid(dashboardState = dashboardState)
                }

                // Cooldown Registers
                item {
                    SectionHeader(title = "Custom Cooldown Registers", icon = Icons.Default.Build)
                }
                item {
                    CooldownSection(
                        dashboardState = dashboardState,
                        onReset7Star = { viewModel.reset7StarTimer() },
                        onResetRecasting = { viewModel.resetRecastingTimer() }
                    )
                }

                // Grind Targets section (Daily Checklist)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSlate),
                        border = BorderStroke(1.dp, BorderSlate)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        tint = AccentGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "DAILY CHECKLIST",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        letterSpacing = 1.sp
                                    )
                                }
                                
                                val completedCount = tasks.count { it.isCompleted }
                                val totalCount = tasks.size
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "$completedCount/$totalCount",
                                        color = JadeGreen,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    TextButton(
                                        onClick = { viewModel.resetAllDailies() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("reset_all_dailies_button"),
                                        colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                                    ) {
                                        Text(
                                            text = "RESET ALL",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            
                            Divider(color = BorderSlate, modifier = Modifier.padding(vertical = 12.dp))
                            
                            val dailyTasks = tasks.filter { it.category == "DAILY" }
                            val sageTrunkTasks = tasks.filter { it.category.startsWith("SAGE_TRUNK") }
                            
                            if (dailyTasks.isEmpty() && sageTrunkTasks.isEmpty()) {
                                Text(
                                    text = "No focus targets found. Sync parameters.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    dailyTasks.forEach { task ->
                                        TaskItemRow(
                                            task = task,
                                            onToggle = { isChecked -> viewModel.toggleTaskStatus(task.id, isChecked) }
                                        )
                                    }
                                    if (sageTrunkTasks.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "SAGE TRUNK ACTIVITIES",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentGold,
                                            letterSpacing = 0.5.sp,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                        sageTrunkTasks.forEach { task ->
                                            TaskItemRow(
                                                task = task,
                                                onToggle = { isChecked -> viewModel.toggleTaskStatus(task.id, isChecked) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Sticky Footer Button
            FooterSection(onRandomClick = { viewModel.selectRandomTask() })
        }

        // Highlight Pop-Up Overlay
        if (highlightedTask != null) {
            CardHighlightOverlay(
                taskMessage = highlightedTask!!,
                onDismiss = { viewModel.dismissRandomTaskHighlight() }
            )
        }
    }
}

@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "SYSTEM CORE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = JadeGreen,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Companion v2.8",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "JUN 04, 2026",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = AccentGold
            )
            Text(
                text = "CYCLE: ACTIVE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun FooterSection(onRandomClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(OnyxBlack)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Button(
            onClick = onRandomClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentGold,
                contentColor = OnyxBlack
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("random_task_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "✦",
                    fontSize = 16.sp,
                    color = OnyxBlack,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GIVE ME A RANDOM TASK",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = AccentGold, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun BiWeeklyCycleCard(dashboardState: DashboardState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bi_weekly_cycle_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dashboardState.biWeeklyActive.ifEmpty { "Loading..." },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = AccentGold
                )
                
                Box(
                    modifier = Modifier
                        .background(JadeGreen.copy(0.1f), RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, JadeGreen.copy(0.2f)), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "CURRENT CYCLE",
                        color = JadeGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Text(
                text = if (dashboardState.biWeeklyActive == "Deity Slain") {
                    "Active Event: Defeat elite world bosses for ancient artifacts"
                } else {
                    "Active Event: Exchange high-value resources and cargo"
                },
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Dynamic scaling countdown container
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OnyxBlack.copy(0.5f), RoundedCornerShape(14.dp))
                    .border(BorderStroke(1.dp, BorderSlate.copy(0.4f)), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                val containerWidth = maxWidth
                // Dynamically compute font sizes based on width classes
                val timerFontSize = if (containerWidth < 300.dp) 16.sp 
                                    else if (containerWidth < 340.dp) 21.sp 
                                    else if (containerWidth < 380.dp) 25.sp 
                                    else 29.sp
                val labelFontSize = if (containerWidth < 300.dp) 7.sp 
                                    else if (containerWidth < 340.dp) 8.sp 
                                    else 9.sp

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dashboardState.biWeeklyTimer,
                        fontSize = timerFontSize,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = "D:H:M:S REMAIN",
                        fontSize = labelFontSize,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Modern 4dp thin progress line matching HTML bar
            LinearProgressIndicator(
                progress = { 0.85f },
                color = JadeGreen,
                trackColor = BorderSlate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
fun RealmMerchantsPanel(dashboardState: DashboardState) {
    RealmShopGrid(dashboardState = dashboardState)
}

@Composable
fun RealmShopGrid(dashboardState: DashboardState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                RealmStatusCard(
                    title = "Time Realm",
                    subtitle = "Monthly 1st-14th Only",
                    isActive = dashboardState.timeRealmActive,
                    timerVal = dashboardState.timeRealmTimer,
                    icon = Icons.Default.Info,
                    activeColor = JadeGreen,
                    testTag = "time_realm_card"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                RealmStatusCard(
                    title = "Demonic Realm",
                    subtitle = "Sat 11:00 am - Sun 23:59",
                    isActive = dashboardState.demonicRealmActive,
                    timerVal = dashboardState.demonicRealmTimer,
                    icon = Icons.Default.Warning,
                    activeColor = Color.Red,
                    testTag = "demonic_realm_card"
                )
            }
        }
        RealmStatusCard(
            title = "Pill Shop Open Calendar",
            subtitle = "Saturdays & Sundays All-Day",
            isActive = dashboardState.pillShopActive,
            timerVal = dashboardState.pillShopTimer,
            icon = Icons.Default.ShoppingCart,
            activeColor = AccentGold,
            testTag = "pill_shop_card"
        )
    }
}

@Composable
fun RealmStatusCard(
    title: String,
    subtitle: String,
    isActive: Boolean,
    timerVal: String,
    icon: ImageVector,
    activeColor: Color,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, if (isActive) activeColor.copy(0.4f) else BorderSlate)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) activeColor else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = if (isActive) "● Active Status" else "○ Locked",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) activeColor else TextSecondary
            )

            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextSecondary
            )

            if (timerVal.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timerVal,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun CooldownSection(
    dashboardState: DashboardState,
    onReset7Star: () -> Unit,
    onResetRecasting: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column {
            // Header row of the combined component
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MANUAL REGISTERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Tactical Cooldowns",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            
            Divider(color = BorderSlate, thickness = 1.dp)
            
            // Items Container
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Item 1: 7 Star Array
                CooldownItemRow(
                    title = "7 Star Array",
                    timerText = dashboardState.sevenStarTimer,
                    resetTag = "reset_7star_button",
                    onResetClick = onReset7Star,
                    progress = dashboardState.sevenStarProgress
                )
                
                // Item 2: Recasting Pool
                CooldownItemRow(
                    title = "Recasting Pool",
                    timerText = dashboardState.recastingTimer,
                    resetTag = "reset_recasting_button",
                    onResetClick = onResetRecasting,
                    progress = dashboardState.recastingProgress
                )
            }
        }
    }
}

@Composable
fun CooldownItemRow(
    title: String,
    timerText: String,
    resetTag: String,
    onResetClick: () -> Unit,
    progress: Float
) {
    val isReady = timerText.contains("Ready")
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OnyxBlack, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, BorderSlate.copy(0.6f)), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = timerText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isReady) JadeGreen else TextSecondary,
                    fontWeight = if (isReady) FontWeight.Bold else FontWeight.Normal
                )
            }
            
            Button(
                onClick = onResetClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReady) JadeGreen else BorderSlate,
                    contentColor = if (isReady) OnyxBlack else Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag(resetTag)
            ) {
                Text(
                    text = "RESET",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = if (isReady) JadeGreen else AccentGold,
            trackColor = BorderSlate,
        )
    }
}

@Composable
fun TaskItemRow(
    task: DbTask,
    onToggle: (Boolean) -> Unit
) {
    val opacity = if (task.isCompleted) 1.0f else 0.6f
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!task.isCompleted) }
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("task_item_${task.id}")
            .graphicsLayer(alpha = opacity),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
         Box(
             modifier = Modifier
                 .size(20.dp)
                 .border(
                     width = 2.dp,
                     color = if (task.isCompleted) JadeGreen else BorderSlate,
                     shape = RoundedCornerShape(4.dp)
                 )
                 .background(
                     color = if (task.isCompleted) JadeGreen else Color.Transparent,
                     shape = RoundedCornerShape(4.dp)
                 )
                 .testTag("task_check_${task.id}"),
             contentAlignment = Alignment.Center
         ) {
             if (task.isCompleted) {
                 Text(
                     text = "✓",
                     color = OnyxBlack,
                     fontSize = 11.sp,
                     fontWeight = FontWeight.Black
                 )
             }
         }
         
         Column(modifier = Modifier.weight(1f)) {
             Text(
                 text = task.title,
                 fontSize = 13.sp,
                 color = if (task.isCompleted) TextPrimary else TextSecondary,
                 fontWeight = if (task.isCompleted) FontWeight.SemiBold else FontWeight.Normal
             )
             Text(
                 text = when (task.category) {
                     "DAILY" -> "Reset Midnight"
                     "SAGE_TRUNK_DAILY" -> "Reset Midnight"
                     "SAGE_TRUNK_WEEKLY" -> "Reset Monday 00:00"
                     else -> "Companion Track"
                 },
                 fontSize = 10.sp,
                 color = TextSecondary.copy(0.7f)
             )
         }
    }
}

@Composable
fun CardHighlightOverlay(
    taskMessage: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = JadeGreen, contentColor = OnyxBlack),
                modifier = Modifier.testTag("dismiss_dialog_button")
            ) {
                Text("Engage Target", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = AccentGold)
                Text(
                    text = "Tactical Recommendation",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Recommended Focus Target:",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = taskMessage,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    lineHeight = 20.sp
                )
            }
        },
        containerColor = DarkSlate,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag("priority_task_dialog")
    )
}

@Composable
fun GridLayout(
    columns: Int,
    spacing: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(content = content) { measurables, constraints ->
        val itemWidth = (constraints.maxWidth - (spacing.roundToPx() * (columns - 1))) / columns
        val itemConstraints = constraints.copy(minWidth = itemWidth, maxWidth = itemWidth)
        val placeables = measurables.map { it.measure(itemConstraints) }
        
        val rows = (placeables.size + columns - 1) / columns
        var totalHeight = 0
        val rowHeights = IntArray(rows)
        
        for (i in 0 until rows) {
            var maxHeight = 0
            for (j in 0 until columns) {
                val index = i * columns + j
                if (index < placeables.size) {
                    maxHeight = maxOf(maxHeight, placeables[index].height)
                }
            }
            rowHeights[i] = maxHeight
            totalHeight += maxHeight + (if (i < rows - 1) spacing.roundToPx() else 0)
        }
        
        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            for (i in 0 until rows) {
                var x = 0
                for (j in 0 until columns) {
                    val index = i * columns + j
                    if (index < placeables.size) {
                        placeables[index].placeRelative(x, y)
                    }
                    x += itemWidth + spacing.roundToPx()
                }
                y += rowHeights[i] + spacing.roundToPx()
            }
        }
    }
}

@Composable
fun StatDisplayTile(label: String, value: Int, baseline: Int) {
    val growth = value - baseline
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(OnyxBlack.copy(0.3f), RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, BorderSlate.copy(0.4f)), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = String.format("%,d", value),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = if (growth >= 0) "+${String.format("%,d", growth)}" else "${String.format("%,d", growth)}",
                fontSize = 10.sp,
                color = if (growth >= 0) JadeGreen else Color.Red,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ManualStatsEditDialog(
    stats: DbStats,
    onDismiss: () -> Unit,
    onSubmit: (Int, Int, Int, Int, Int) -> Unit
) {
    var strengthStr by remember { mutableStateOf(stats.strength.toString()) }
    var agilityStr by remember { mutableStateOf(stats.agility.toString()) }
    var physiqueStr by remember { mutableStateOf(stats.physique.toString()) }
    var intellectStr by remember { mutableStateOf(stats.intellect.toString()) }
    var elementalStr by remember { mutableStateOf(stats.elemental.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Stats Manually", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        containerColor = DarkSlate,
        confirmButton = {
            Button(
                onClick = {
                    val str = strengthStr.replace(",", "").replace(".", "").toIntOrNull() ?: stats.strength
                    val agl = agilityStr.replace(",", "").replace(".", "").toIntOrNull() ?: stats.agility
                    val phy = physiqueStr.replace(",", "").replace(".", "").toIntOrNull() ?: stats.physique
                    val intel = intellectStr.replace(",", "").replace(".", "").toIntOrNull() ?: stats.intellect
                    val elem = elementalStr.replace(",", "").replace(".", "").toIntOrNull() ?: stats.elemental
                    onSubmit(str, agl, phy, intel, elem)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = JadeGreen, contentColor = OnyxBlack),
                modifier = Modifier.testTag("submit_manual_stats_btn")
            ) {
                Text("Save Stats", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = strengthStr,
                    onValueChange = { strengthStr = it },
                    label = { Text("Strength") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = BorderSlate,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = AccentGold,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("manual_strength_input")
                )
                OutlinedTextField(
                    value = agilityStr,
                    onValueChange = { agilityStr = it },
                    label = { Text("Agility") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = BorderSlate,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = AccentGold,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("manual_agility_input")
                )
                OutlinedTextField(
                    value = physiqueStr,
                    onValueChange = { physiqueStr = it },
                    label = { Text("Health") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = BorderSlate,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = AccentGold,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("manual_physique_input")
                )
                OutlinedTextField(
                    value = intellectStr,
                    onValueChange = { intellectStr = it },
                    label = { Text("Qi") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = BorderSlate,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = AccentGold,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("manual_intellect_input")
                )
                OutlinedTextField(
                    value = elementalStr,
                    onValueChange = { elementalStr = it },
                    label = { Text("Water DMG") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = BorderSlate,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = AccentGold,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("manual_elemental_input")
                )
            }
        }
    )
}

@Composable
fun StatScannerSection(
    stats: DbStats,
    isScanning: Boolean,
    scanningState: String?,
    onScanClick: (String) -> Unit,
    onSelectRealImage: () -> Unit,
    onManualSubmit: (Int, Int, Int, Int, Int) -> Unit,
    onResetBaseline: () -> Unit
) {
    var showManualDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stat_scanner_section_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "GROWTH CALCULATOR",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }
                
                // Fallback Manual button
                IconButton(
                    onClick = { showManualDialog = true },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("manual_edit_stats_icon_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Stats Manually",
                        tint = AccentGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Divider(color = BorderSlate)

            // Image Dropbox mockup
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OnyxBlack.copy(0.4f), RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, BorderSlate.copy(0.6f)), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(color = JadeGreen, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = scanningState ?: "Extracting attributes...",
                        fontSize = 12.sp,
                        color = JadeGreen,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("scanning_status_logs")
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Stat Screenshot Scanner",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Upload game attribute screen to auto-calculate gains:",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSelectRealImage() },
                            colors = ButtonDefaults.buttonColors(containerColor = JadeGreen, contentColor = OnyxBlack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("upload_stat_screenshot_btn")
                        ) {
                            Text("UPLOAD STAT SCREENSHOT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { onScanClick("SUCCESS_ALL") },
                                colors = ButtonDefaults.buttonColors(containerColor = BorderSlate.copy(0.6f), contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(32.dp).testTag("sim_pass_all_btn")
                            ) {
                                Text("MOCK COMPLETE", fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { onScanClick("SUCCESS_FOUR_DIM") },
                                colors = ButtonDefaults.buttonColors(containerColor = BorderSlate.copy(0.6f), contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(32.dp).testTag("sim_four_dim_btn")
                            ) {
                                Text("MOCK FOUR DIM", fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { onScanClick("PARTIAL_PROGRESS") },
                                colors = ButtonDefaults.buttonColors(containerColor = BorderSlate.copy(0.6f), contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(32.dp).testTag("sim_partial_btn")
                            ) {
                                Text("MOCK PROGRESS", fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Extracted stats values grid
            Text(
                text = "EXTRACTED STATS TODAY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold,
                letterSpacing = 0.5.sp
            )

            GridLayout(columns = 2, spacing = 8.dp) {
                StatDisplayTile(label = "STRENGTH", value = stats.strength, baseline = stats.baselineStrength)
                StatDisplayTile(label = "AGILITY", value = stats.agility, baseline = stats.baselineAgility)
                StatDisplayTile(label = "HEALTH", value = stats.physique, baseline = stats.baselinePhysique)
                StatDisplayTile(label = "QI", value = stats.intellect, baseline = stats.baselineIntellect)
            }
            
            // Major row for Water DMG.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OnyxBlack.copy(0.3f), RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, BorderSlate.copy(0.4f)), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("WATER DMG.", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format("%,d", stats.elemental),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold
                        )
                    }
                    val elementalGrowth = stats.elemental - stats.baselineElemental
                    Column(horizontalAlignment = Alignment.End) {
                        Text("GROWTH", fontSize = 9.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (elementalGrowth >= 0) "+${String.format("%,d", elementalGrowth)}" else "${String.format("%,d", elementalGrowth)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (elementalGrowth >= 50000) JadeGreen else TextPrimary
                        )
                    }
                }
            }
                   // Growth Progress and badges
            val qiGrowth = stats.intellect - stats.baselineIntellect
            val strGrowth = stats.strength - stats.baselineStrength
            val healthGrowth = stats.physique - stats.baselinePhysique
            val agiGrowth = stats.agility - stats.baselineAgility
            val elementalGrowth = stats.elemental - stats.baselineElemental

            val isAllFourGrown = qiGrowth >= 50000 && strGrowth >= 50000 && healthGrowth >= 50000 && agiGrowth >= 50000
            val completedDims = (if (qiGrowth >= 50000) 1 else 0) +
                    (if (strGrowth >= 50000) 1 else 0) +
                    (if (healthGrowth >= 50000) 1 else 0) +
                    (if (agiGrowth >= 50000) 1 else 0)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OnyxBlack.copy(0.3f), RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, BorderSlate), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "STAT GROWTH SINCE LAST RESET",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )

                // 4 Dimensions Goal UI
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("4 Dimensions (+50k Each)", fontSize = 11.sp, color = TextSecondary)
                        if (isAllFourGrown) {
                            Box(
                                modifier = Modifier
                                    .background(JadeGreen.copy(0.15f), RoundedCornerShape(6.dp))
                                    .border(BorderStroke(1.dp, JadeGreen.copy(0.3f)), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("50k Daily Target Achieved!", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = JadeGreen)
                            }
                        } else {
                            Text(
                                text = "$completedDims/4 reached",
                                fontSize = 10.sp,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Qi: ${if(qiGrowth >= 50000) "✓ 50k" else "+${String.format("%,d", qiGrowth)}"} | Str: ${if(strGrowth >= 50000) "✓ 50k" else "+${String.format("%,d", strGrowth)}"} | Health: ${if(healthGrowth >= 50000) "✓ 50k" else "+${String.format("%,d", healthGrowth)}"} | Agi: ${if(agiGrowth >= 50000) "✓ 50k" else "+${String.format("%,d", agiGrowth)}"}",
                        fontSize = 9.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val progressDim = ((qiGrowth.toFloat().coerceIn(0f, 50000f) + strGrowth.toFloat().coerceIn(0f, 50000f) + healthGrowth.toFloat().coerceIn(0f, 50000f) + agiGrowth.toFloat().coerceIn(0f, 50000f)) / 200000f)
                    LinearProgressIndicator(
                        progress = { progressDim },
                        color = if (isAllFourGrown) JadeGreen else AccentGold,
                        trackColor = BorderSlate,
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                    )
                }

                // Elemental Goal UI
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Water DMG. (+50k Milestone)", fontSize = 11.sp, color = TextSecondary)
                        if (elementalGrowth >= 50000) {
                            Box(
                                modifier = Modifier
                                    .background(JadeGreen.copy(0.15f), RoundedCornerShape(6.dp))
                                    .border(BorderStroke(1.dp, JadeGreen.copy(0.3f)), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("50k Daily Target Achieved!", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = JadeGreen)
                            }
                        } else {
                            Text(
                                text = "${String.format("%,d", elementalGrowth)} / 50,000 gained",
                                fontSize = 10.sp,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val progressElem = (elementalGrowth.toFloat() / 50000f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressElem },
                        color = if (elementalGrowth >= 50000) JadeGreen else AccentGold,
                        trackColor = BorderSlate,
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                    )
                }
            }

            // Manual fallbacks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showManualDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentGold),
                    modifier = Modifier.testTag("edit_skills_fallback_btn")
                ) {
                    Text("Manual Upload Fallback", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                
                TextButton(
                    onClick = onResetBaseline,
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
                    modifier = Modifier.testTag("stats_reset_baseline_manual_btn")
                ) {
                    Text("🔄 Reset Baseline to Current", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showManualDialog) {
        ManualStatsEditDialog(
            stats = stats,
            onDismiss = { showManualDialog = false },
            onSubmit = onManualSubmit
        )
    }
}

@Composable
fun ActiveAlertsBanners(
    alerts: List<AlertItem>,
    onChimeClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().testTag("active_alerts_banners_col")
    ) {
        alerts.forEach { alert ->
            val colorBase = if (alert.id == "jinzhou_close" || alert.id == "stat_cutoff") Color(0xFFFF5252) else AccentGold
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorBase.copy(0.08f), RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.2.dp, colorBase.copy(0.4f)), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (alert.id == "jinzhou_close" || alert.id == "stat_cutoff") Icons.Default.Warning else Icons.Default.Notifications,
                        contentDescription = null,
                        tint = colorBase,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = alert.title.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorBase,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "• ${alert.timeLabel}",
                                fontSize = 9.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = alert.content,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Button to manually playback alarm chime
                    IconButton(
                        onClick = onChimeClick,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("test_alert_chime_btn_${alert.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Trigger Reminder Beep Chime",
                            tint = colorBase,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimeSimulationConsole(
    timeLabel: String,
    onSimulateTime: (Int, Int) -> Unit,
    onResetTime: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("time_simulation_console_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SlateVariant),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ALERT TIME SIMULATOR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(AccentGold.copy(0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = timeLabel,
                        color = AccentGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            if (isExpanded) {
                Divider(color = BorderSlate.copy(0.6f))
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Simulate game clock positions to immediately examine targeted window banners & pings:",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    
                    GridLayout(columns = 2, spacing = 8.dp) {
                        Button(
                            onClick = { onSimulateTime(11, 0) },
                            colors = ButtonDefaults.buttonColors(containerColor = BorderSlate),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp).testTag("btn_sim_11am")
                        ) {
                            Text("11:00 AM (Jinzhou Open)", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onSimulateTime(16, 0) },
                            colors = ButtonDefaults.buttonColors(containerColor = BorderSlate),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp).testTag("btn_sim_4pm")
                        ) {
                            Text("4:00 PM (Grind Remind)", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onSimulateTime(20, 30) },
                            colors = ButtonDefaults.buttonColors(containerColor = BorderSlate),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp).testTag("btn_sim_830pm")
                        ) {
                            Text("8:30 PM (Jinzhou Closing)", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onSimulateTime(22, 0) },
                            colors = ButtonDefaults.buttonColors(containerColor = BorderSlate),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp).testTag("btn_sim_10pm")
                        ) {
                            Text("10:00 PM (Photo Cutoff)", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Button(
                        onClick = onResetTime,
                        colors = ButtonDefaults.buttonColors(containerColor = JadeGreen, contentColor = OnyxBlack),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp).testTag("btn_sim_reset")
                    ) {
                        Text("RESET TO LIVING DEVICE CLOCK", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
