package com.scrollguard.app.presentation.strictmode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollguard.app.presentation.components.PinPromptDialog
import com.scrollguard.app.presentation.components.SectionCard
import com.scrollguard.app.presentation.components.StatusPill
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StrictModeScreen(
    viewModel: StrictModeViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strict = state.strictModeState

    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryInput by remember { mutableStateOf("") }

    if (state.showPinDialog) {
        PinPromptDialog(
            errorMessage = state.pinError,
            onDismiss = { viewModel.dismissPinDialog() },
            onSubmitPin = { viewModel.verifyPinForPendingAction(it) }
        )
    }

    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text("Emergency Recovery") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter the 8-character recovery key generated when you created your PIN to reset Strict Mode.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = recoveryInput,
                        onValueChange = { recoveryInput = it.uppercase() },
                        label = { Text("Recovery Key") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.recoverWithKey(recoveryInput)
                        recoveryInput = ""
                        showRecoveryDialog = false
                    },
                    enabled = recoveryInput.trim().length >= 6
                ) {
                    Text("Recover")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Strict Mode & Focus") })
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
            state.statusMessage?.let {
                SectionCard {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            state.errorMessage?.let {
                SectionCard {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            state.generatedRecoveryCode?.let { code ->
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Save Your Emergency Recovery Key",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = code,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Store this code offline. ScrollGuard never stores your plain-text PIN and cannot recover it without this key.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { viewModel.clearRecoveryBanner() }) {
                            Text("I've saved it")
                        }
                    }
                }
            }

            // Strict Mode Toggle Card
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Strict Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Locks rule, app, and schedule edits behind your PIN.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = strict.isStrictModeEnabled,
                            onCheckedChange = viewModel::toggleStrictMode
                        )
                    }
                    StatusPill(
                        text = if (strict.isStrictModeEnabled) "PIN Protected" else "Normal Mode",
                        isPositive = strict.isStrictModeEnabled
                    )
                    Text(
                        text = "Note: Strict Mode is an intentional self-control lock inside ScrollGuard and does not block Android OS system settings or uninstall.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Focus Session Card
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Focus Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (strict.focusSessionActive && state.focusRemainingSeconds > 0L) {
                        val mins = state.focusRemainingSeconds / 60L
                        val secs = state.focusRemainingSeconds % 60L
                        Text(
                            text = String.format(Locale.US, "%02d:%02d remaining", mins, secs),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { viewModel.endFocusSession() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("End Focus Session")
                        }
                    } else {
                        Text(
                            text = "Select duration (default 60 minutes):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(25, 45, 60, 90, 120).forEach { mins ->
                                FilterChip(
                                    selected = strict.focusSessionDurationMinutes == mins,
                                    onClick = { viewModel.setFocusDurationMinutes(mins) },
                                    label = { Text("${mins}m") }
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.startFocusSession() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Start ${strict.focusSessionDurationMinutes}-Minute Focus Session")
                        }
                    }
                }
            }

            // Temporary Unlocks Quota
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Daily Temporary Unlocks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Used ${strict.tempUnlocksUsedToday} of ${strict.dailyTempUnlockLimit} today (5 minutes each).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 1, 2, 3, 5).forEach { quota ->
                            FilterChip(
                                selected = strict.dailyTempUnlockLimit == quota,
                                onClick = { viewModel.setDailyTempUnlockLimit(quota) },
                                label = { Text(if (quota == 0) "None" else "$quota/day") }
                            )
                        }
                    }
                }
            }

            // PIN Setup or Emergency Recovery
            if (!strict.hasPinSet) {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Create Protection PIN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                            label = { Text("New PIN (4-8 digits)") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmPinInput,
                            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmPinInput = it },
                            label = { Text("Confirm PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                viewModel.createPin(pinInput, confirmPinInput)
                                pinInput = ""
                                confirmPinInput = ""
                            },
                            enabled = pinInput.length >= 4 && confirmPinInput.length >= 4,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Save PIN")
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { showRecoveryDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Emergency Recovery (Use Recovery Key)")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
