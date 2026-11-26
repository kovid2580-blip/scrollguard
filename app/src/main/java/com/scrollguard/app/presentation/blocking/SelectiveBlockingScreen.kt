package com.scrollguard.app.presentation.blocking

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollguard.app.presentation.components.AppIconImage
import com.scrollguard.app.presentation.components.PinPromptDialog
import com.scrollguard.app.presentation.components.SectionCard
import com.scrollguard.app.presentation.components.StatusPill
import com.scrollguard.app.presentation.theme.AmberContainer
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectiveBlockingScreen(
    viewModel: SelectiveBlockingViewModel,
    onOpenDiagnostics: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCustomDialog by remember { mutableStateOf(false) }

    if (state.showPinDialog) {
        PinPromptDialog(
            errorMessage = state.pinError,
            onDismiss = { viewModel.dismissPinDialog() },
            onSubmitPin = { viewModel.verifyPin(it) }
        )
    }

    if (showCustomDialog) {
        CustomRuleDialog(
            onDismiss = { showCustomDialog = false },
            onSave = { pkg, name, resIds, descs, keywords, exclusions, conf ->
                viewModel.saveCustomRule(pkg, name, resIds, descs, keywords, exclusions, conf)
                showCustomDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Content Filtering") },
                actions = {
                    if (state.strictLocked) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Strict Mode Locked",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Rounded.BugReport, contentDescription = "Diagnostics")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCustomDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Custom Rule")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "System-Wide Content Filtering suppresses identifiable short-form navigation tabs and interrupts restricted short-video players while keeping core messaging, search, and regular feeds usable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Entry-Point Tab Suppression",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Places bounded shields over static Reels/Shorts/Spotlight nav tabs when detectable. Dynamic scrolling shelves are handled by immediate player exit.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = state.entryPointSuppressionEnabled,
                        onCheckedChange = viewModel::setEntryPointSuppressionEnabled
                    )
                }
            }

            state.builtInRuleItems.forEach { item ->
                SelectiveRuleCard(
                    item = item,
                    onToggle = { enabled -> viewModel.toggleRule(item.rule, enabled) },
                    onOpenDiagnostics = onOpenDiagnostics,
                    onDelete = null
                )
            }

            if (state.customRuleItems.isNotEmpty()) {
                Text(
                    text = "Custom Content Rules",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
                state.customRuleItems.forEach { item ->
                    SelectiveRuleCard(
                        item = item,
                        onToggle = { enabled -> viewModel.toggleRule(item.rule, enabled) },
                        onOpenDiagnostics = onOpenDiagnostics,
                        onDelete = { viewModel.deleteCustomRule(item.rule.id) }
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenDiagnostics,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.BugReport, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Detection Diagnostics")
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
private fun SelectiveRuleCard(
    item: SelectiveRuleStatusUi,
    onToggle: (Boolean) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val rule = item.rule
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(
                    packageName = rule.packageName,
                    appName = rule.appName,
                    size = 40.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.contentType.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = rule.packageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete Rule")
                    }
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle
                )
            }

            Text(
                text = rule.detectionConfig.compatibilityNote.ifBlank { rule.contentType.description },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Entry points: ${item.entryPointStatusLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Detection: ${item.detectionStatusLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            item.lastResultSummary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (item.uiNeedsReviewWarning) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = Color(0xFF92400E)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "This app's UI exposed limited nodes; rules may need updating.",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF78350F)
                            )
                        }
                        TextButton(onClick = onOpenDiagnostics) {
                            Text("Inspect")
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = if (rule.enabled) "Active" else "Off",
                    isPositive = rule.enabled
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "Min confidence: %.0f%%",
                        rule.detectionConfig.minConfidence * 100f
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CustomRuleDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, Float) -> Unit
) {
    var packageName by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }
    var resourceIds by remember { mutableStateOf("") }
    var contentDescs by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var exclusions by remember { mutableStateOf("") }
    var minConfidence by remember { mutableFloatStateOf(0.65f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Content Rule") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Target Package ID (e.g. com.example.app)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Display Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = resourceIds,
                    onValueChange = { resourceIds = it },
                    label = { Text("Resource ID Keywords (comma-separated)") }
                )
                OutlinedTextField(
                    value = contentDescs,
                    onValueChange = { contentDescs = it },
                    label = { Text("Content Descriptions (comma-separated)") }
                )
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("Text Keywords (comma-separated)") }
                )
                OutlinedTextField(
                    value = exclusions,
                    onValueChange = { exclusions = it },
                    label = { Text("Exclusion Keywords (comma-separated)") }
                )
                Text(
                    text = String.format(Locale.US, "Confidence threshold: %.0f%%", minConfidence * 100f),
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = minConfidence,
                    onValueChange = { minConfidence = it },
                    valueRange = 0.45f..0.90f
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(packageName, appName, resourceIds, contentDescs, keywords, exclusions, minConfidence)
                },
                enabled = packageName.contains(".")
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
