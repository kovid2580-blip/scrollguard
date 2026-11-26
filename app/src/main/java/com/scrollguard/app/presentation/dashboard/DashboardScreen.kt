package com.scrollguard.app.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollguard.app.presentation.components.ScrollGuardShieldBadge
import com.scrollguard.app.presentation.components.SectionCard
import com.scrollguard.app.presentation.components.StatusPill
import com.scrollguard.app.presentation.components.formatDurationShort
import com.scrollguard.app.presentation.theme.AmberContainer
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenPermissions: () -> Unit,
    onOpenSelectiveBlocking: () -> Unit,
    onOpenAppBlocking: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenStrictMode: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDashboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScrollGuardShieldBadge(size = 34.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("ScrollGuard", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshDashboard() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh usage")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!state.permissionStatus.allCoreGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color(0xFF92400E)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Setup incomplete — blocking is limited",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = androidx.compose.ui.graphics.Color(0xFF78350F)
                            )
                        }
                        Text(
                            text = "Grant Accessibility Service, Usage Access, and Overlay access so ScrollGuard can detect and block addictive feeds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color(0xFF78350F)
                        )
                        Button(onClick = onOpenPermissions, shape = RoundedCornerShape(12.dp)) {
                            Text("Review Permissions")
                        }
                    }
                }
            }

            // Protection Status Card
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Protection Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    state.strictModeState.isStrictModeEnabled -> "Strict Mode active"
                                    state.strictModeState.isBlockingPaused -> "Paused (Normal Mode)"
                                    else -> "Active — Normal Mode"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = if (state.strictModeState.isBlockingPaused) "Paused" else "Protected",
                            isPositive = !state.strictModeState.isBlockingPaused
                        )
                    }

                    if (state.strictModeState.focusSessionActive && state.focusRemainingSeconds > 0L) {
                        val mins = state.focusRemainingSeconds / 60L
                        val secs = state.focusRemainingSeconds % 60L
                        Text(
                            text = String.format(
                                Locale.US,
                                "Focus Session Countdown: %02d:%02d remaining",
                                mins,
                                secs
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!state.strictModeState.isSettingsLocked()) {
                            OutlinedButton(
                                onClick = { viewModel.toggleNormalModePause() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.strictModeState.isBlockingPaused) {
                                        Icons.Rounded.PlayArrow
                                    } else {
                                        Icons.Rounded.Pause
                                    },
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (state.strictModeState.isBlockingPaused) "Resume" else "Pause")
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                if (state.strictModeState.focusSessionActive) {
                                    onOpenStrictMode()
                                } else {
                                    viewModel.startQuickFocusSession()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Timer, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (state.strictModeState.focusSessionActive) {
                                    "Manage Session"
                                } else {
                                    "Start ${state.strictModeState.focusSessionDurationMinutes}m Focus"
                                }
                            )
                        }
                    }
                }
            }

            // Metrics Grid (2x2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Today's Screen Time",
                    value = if (state.permissionStatus.usageAccessGranted) {
                        formatDurationShort(state.todayScreenTimeMs)
                    } else {
                        "No Access"
                    },
                    subtitle = "Measured foreground",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Blocked Attempts",
                    value = state.blockedAttemptsToday.toString(),
                    subtitle = "Interrupted today",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Protected Apps",
                    value = state.protectedAppsCount.toString(),
                    subtitle = "${state.selectiveRulesActiveCount} selective rules",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Active Schedules",
                    value = state.activeSchedulesCount.toString(),
                    subtitle = if (state.strictModeState.isStrictModeEnabled) "Strict PIN ON" else "Normal Mode",
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick Actions
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Quick Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onOpenSelectiveBlocking,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.FilterList, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reels & Shorts")
                        }
                        FilledTonalButton(
                            onClick = onOpenAppBlocking,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Apps, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Full Apps")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenSchedules,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Schedules")
                        }
                        OutlinedButton(
                            onClick = onOpenStrictMode,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Strict Mode")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
