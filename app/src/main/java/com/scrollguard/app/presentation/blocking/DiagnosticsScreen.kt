package com.scrollguard.app.presentation.blocking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollguard.app.presentation.components.SectionCard
import com.scrollguard.app.presentation.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: SelectiveBlockingViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val live = state.liveDiagnostics
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detection Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearDiagnostics() }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear buffer")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Developer Diagnostics Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Logs package, screen classification, confidence, navigation state, and overlay bounds in RAM only. Zero raw screen text or screenshots.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.diagnosticsEnabled,
                        onCheckedChange = viewModel::setDiagnosticsEnabled
                    )
                }
            }

            // Live State Machine & Entry-Point Telemetry Card
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Detection Snapshot",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        StatusPill(
                            text = if (live.entryPointOverlayActive) "Tab Overlay Active" else "Tab Overlay Inactive",
                            isPositive = live.entryPointOverlayActive
                        )
                    }
                    DiagnosticLine("Foreground package", live.foregroundPackage)
                    DiagnosticLine("Screen classification", live.screenClassification)
                    DiagnosticLine(
                        "Detection confidence",
                        String.format(Locale.US, "%.0f%%", live.reelsConfidence * 100f)
                    )
                    DiagnosticLine("Entry-point bounds", live.entryPointBounds)
                    DiagnosticLine("Session state", "${live.navigationState} (cooldown=${live.cooldownActive})")
                    DiagnosticLine(
                        "Navigation actions",
                        "${live.navigationActionCount} total (last: ${live.lastNavigationAction})"
                    )
                    DiagnosticLine("Last blocking decision", live.lastBlockingDecision)
                    DiagnosticLine("Last failure / note", live.lastDetectionFailureReason)
                    DiagnosticLine("Audio active check", if (live.audioActiveAfterInterrupt) "Active" else "Silent / Inactive")
                }
            }

            if (state.diagnosticEvents.isEmpty()) {
                Text(
                    text = if (state.diagnosticsEnabled) {
                        "Listening for accessibility events on monitored apps..."
                    } else {
                        "Enable Developer Diagnostics Mode above to record event-by-event trace logs."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.diagnosticEvents, key = { it.id }) { event ->
                        SectionCard {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${event.packageName} • ${event.screenClassification}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = timeFormat.format(Date(event.timestamp)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${event.eventType} • ${event.matchedRule}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%s (%.0f%%) | State: %s | Nav: %s | Cooldown: %s",
                                        event.decision,
                                        event.confidence * 100f,
                                        event.sessionState,
                                        event.navigationAction,
                                        event.cooldownActive
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (event.signals.isNotEmpty()) {
                                    Text(
                                        text = "Signals: ${event.signals.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
