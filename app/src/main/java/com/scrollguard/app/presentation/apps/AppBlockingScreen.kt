package com.scrollguard.app.presentation.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollguard.app.presentation.components.AppIconImage
import com.scrollguard.app.presentation.components.PinPromptDialog
import com.scrollguard.app.presentation.components.SectionCard
import com.scrollguard.app.presentation.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBlockingScreen(
    viewModel: AppBlockingViewModel,
    onOpenSelectiveBlocking: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showPinDialog) {
        PinPromptDialog(
            errorMessage = state.pinError,
            onDismiss = { viewModel.dismissPinDialog() },
            onSubmitPin = { viewModel.verifyPin(it) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Application Blocking") },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search installed apps or packages...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.items, key = { it.app.packageName }) { item ->
                        AppBlockingRow(
                            item = item,
                            onToggleFullBlock = { enabled ->
                                viewModel.toggleFullBlock(item.app, enabled)
                            },
                            onSelectDailyLimit = { limit ->
                                viewModel.setDailyLimit(item.app, limit)
                            },
                            onOpenSelective = onOpenSelectiveBlocking
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AppBlockingRow(
    item: AppBlockingItemUi,
    onToggleFullBlock: (Boolean) -> Unit,
    onSelectDailyLimit: (Int?) -> Unit,
    onOpenSelective: () -> Unit
) {
    var limitMenuOpen by remember { mutableStateOf(false) }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(
                    packageName = item.app.packageName,
                    appName = item.app.appName,
                    size = 44.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.app.packageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = item.fullBlockEnabled,
                    onCheckedChange = onToggleFullBlock
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    OutlinedButton(
                        onClick = { limitMenuOpen = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        val limitLabel = item.dailyLimitMinutes?.let { "${it}m daily limit" }
                            ?: if (item.fullBlockEnabled) "Always blocked" else "No daily limit"
                        Text(limitLabel)
                    }
                    DropdownMenu(
                        expanded = limitMenuOpen,
                        onDismissRequest = { limitMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No daily limit (full toggle only)") },
                            onClick = {
                                limitMenuOpen = false
                                onSelectDailyLimit(null)
                            }
                        )
                        listOf(15, 30, 45, 60, 90, 120).forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text("$minutes minutes / day") },
                                onClick = {
                                    limitMenuOpen = false
                                    onSelectDailyLimit(minutes)
                                }
                            )
                        }
                    }
                }

                if (item.app.supportsSelectiveBlocking) {
                    OutlinedButton(
                        onClick = onOpenSelective,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (item.selectiveRuleActive) "Selective ON" else "Selective"
                        )
                    }
                } else if (item.fullBlockEnabled) {
                    StatusPill(text = "Blocked", isPositive = true)
                }
            }
        }
    }
}
