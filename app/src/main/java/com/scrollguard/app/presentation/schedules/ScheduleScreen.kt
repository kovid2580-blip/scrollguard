package com.scrollguard.app.presentation.schedules

import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollguard.app.domain.model.FocusSchedule
import com.scrollguard.app.domain.model.InstalledApp
import com.scrollguard.app.presentation.components.PinPromptDialog
import com.scrollguard.app.presentation.components.SectionCard
import com.scrollguard.app.presentation.components.StatusPill
import com.scrollguard.app.presentation.components.formatTime12Hour
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingSchedule by remember { mutableStateOf<FocusSchedule?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    if (state.showPinDialog) {
        PinPromptDialog(
            errorMessage = state.pinError,
            onDismiss = { viewModel.dismissPinDialog() },
            onSubmitPin = { viewModel.verifyPin(it) }
        )
    }

    if (showEditor) {
        ScheduleEditorDialog(
            initial = editingSchedule,
            availableApps = state.availableApps,
            onDismiss = {
                showEditor = false
                editingSchedule = null
            },
            onSave = { name, sh, sm, eh, em, days, pkgs, full, selective ->
                viewModel.saveSchedule(
                    existingId = editingSchedule?.id ?: 0L,
                    name = name,
                    startHour = sh,
                    startMinute = sm,
                    endHour = eh,
                    endMinute = em,
                    weekdays = days,
                    affectedPackages = pkgs,
                    appliesToFullApp = full,
                    appliesToSelectiveContent = selective
                )
                showEditor = false
                editingSchedule = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus Schedules") },
                actions = {
                    if (state.strictLocked) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Protected by Strict Mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingSchedule = null
                    showEditor = true
                }
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create Schedule")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (state.schedules.isEmpty()) {
                SectionCard(modifier = Modifier.padding(top = 12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "No schedules yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Create a schedule such as Mon–Fri 10:00 PM – 7:00 AM to automatically enforce full-app or selective blocking during sleep or work hours.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(state.schedules, key = { it.schedule.id }) { item ->
                        ScheduleRowCard(
                            item = item,
                            onToggle = { enabled -> viewModel.toggleSchedule(item.schedule, enabled) },
                            onEdit = {
                                editingSchedule = item.schedule
                                showEditor = true
                            },
                            onDelete = { viewModel.deleteSchedule(item.schedule.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRowCard(
    item: ScheduleItemUi,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val schedule = item.schedule
    val daysLabel = schedule.weekdays.sortedBy { it.value }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.US) }
    val timeWindow = "${formatTime12Hour(schedule.startHour, schedule.startMinute)} – ${
        formatTime12Hour(schedule.endHour, schedule.endMinute)
    }" + if (schedule.isOvernight) " (Overnight)" else ""

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schedule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = timeWindow,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = onToggle
                )
            }

            Text(
                text = "Days: ${daysLabel.ifBlank { "None selected" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val scopeText = buildList {
                if (schedule.appliesToFullApp) add("Full-app block")
                if (schedule.appliesToSelectiveContent) add("Selective content")
            }.joinToString(" + ").ifBlank { "No mode selected" }

            Text(
                text = "$scopeText • ${schedule.affectedPackages.size} app(s) targeted",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = when {
                        item.isCurrentlyActive -> "Active Now"
                        schedule.enabled -> "Scheduled"
                        else -> "Disabled"
                    },
                    isPositive = item.isCurrentlyActive || schedule.enabled
                )
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditorDialog(
    initial: FocusSchedule?,
    availableApps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int, Int, Set<DayOfWeek>, Set<String>, Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "Wind-Down Sleep") }
    var startHour by remember { mutableIntStateOf(initial?.startHour ?: 22) }
    var startMinute by remember { mutableIntStateOf(initial?.startMinute ?: 0) }
    var endHour by remember { mutableIntStateOf(initial?.endHour ?: 7) }
    var endMinute by remember { mutableIntStateOf(initial?.endMinute ?: 0) }
    var weekdays by remember {
        mutableStateOf(
            initial?.weekdays ?: setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            )
        )
    }
    var affectedPackages by remember {
        mutableStateOf(
            initial?.affectedPackages ?: setOf(
                "com.instagram.android",
                "com.google.android.youtube",
                "com.facebook.katana"
            )
        )
    }
    var appliesToFullApp by remember { mutableStateOf(initial?.appliesToFullApp ?: true) }
    var appliesToSelective by remember { mutableStateOf(initial?.appliesToSelectiveContent ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create Schedule" else "Edit Schedule") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Schedule Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    startHour = h
                                    startMinute = m
                                },
                                startHour,
                                startMinute,
                                false
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start: ${formatTime12Hour(startHour, startMinute)}")
                    }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    endHour = h
                                    endMinute = m
                                },
                                endHour,
                                endMinute,
                                false
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("End: ${formatTime12Hour(endHour, endMinute)}")
                    }
                }

                Text("Weekdays", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DayOfWeek.entries.forEach { day ->
                        val selected = day in weekdays
                        FilterChip(
                            selected = selected,
                            onClick = {
                                weekdays = if (selected) weekdays - day else weekdays + day
                            },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.US)) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = appliesToFullApp, onCheckedChange = { appliesToFullApp = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Block full app during window")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = appliesToSelective, onCheckedChange = { appliesToSelective = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply selective Reels/Shorts rules")
                }

                Text("Target Applications", style = MaterialTheme.typography.labelLarge)
                val selectableApps = availableApps.ifEmpty {
                    listOf(
                        InstalledApp("com.instagram.android", "Instagram", false, true),
                        InstalledApp("com.google.android.youtube", "YouTube", false, true),
                        InstalledApp("com.facebook.katana", "Facebook", false, true)
                    )
                }
                selectableApps.take(15).forEach { app ->
                    val checked = app.packageName in affectedPackages
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                affectedPackages = if (isChecked) {
                                    affectedPackages + app.packageName
                                } else {
                                    affectedPackages - app.packageName
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(app.appName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        startHour,
                        startMinute,
                        endHour,
                        endMinute,
                        weekdays,
                        affectedPackages,
                        appliesToFullApp,
                        appliesToSelective
                    )
                },
                enabled = weekdays.isNotEmpty() && (appliesToFullApp || appliesToSelective)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
