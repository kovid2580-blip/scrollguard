package com.scrollguard.app.presentation.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollguard.app.presentation.components.SectionCard
import com.scrollguard.app.presentation.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSetupScreen(
    viewModel: OnboardingViewModel,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onContinueToDashboard: () -> Unit
) {
    val status by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permission Setup") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "ScrollGuard verifies each special Android permission directly from system settings. Grant the permissions below to activate real-time protection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PermissionRowCard(
                title = "1. Accessibility Service",
                description = "Required to detect Reels, Shorts, and restricted app screens without blocking the whole app.",
                isGranted = status.accessibilityEnabled,
                actionLabel = if (status.accessibilityEnabled) "Manage in Settings" else "Enable Service",
                onActionClick = {
                    context.startActivity(viewModel.permissionChecker.accessibilitySettingsIntent())
                }
            )

            PermissionRowCard(
                title = "2. Usage Access",
                description = "Required to measure foreground screen time and enforce daily app usage limits.",
                isGranted = status.usageAccessGranted,
                actionLabel = if (status.usageAccessGranted) "Manage Access" else "Grant Usage Access",
                onActionClick = {
                    context.startActivity(viewModel.permissionChecker.usageAccessSettingsIntent())
                }
            )

            PermissionRowCard(
                title = "3. Display Over Other Apps",
                description = "Required to show the focus reminder overlay when short-form content or blocked apps appear.",
                isGranted = status.overlayGranted,
                actionLabel = if (status.overlayGranted) "Manage Overlay" else "Allow Overlay",
                onActionClick = {
                    context.startActivity(viewModel.permissionChecker.overlaySettingsIntent())
                }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRowCard(
                    title = "4. Notifications (Optional)",
                    description = "Used for focus session alerts and schedule updates on Android 13+.",
                    isGranted = status.notificationsGranted,
                    actionLabel = if (status.notificationsGranted) "Notification Settings" else "Allow Notifications",
                    onActionClick = {
                        if (status.notificationsGranted) {
                            context.startActivity(viewModel.permissionChecker.notificationSettingsIntent())
                        } else {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onContinueToDashboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (status.allCoreGranted) "All Core Permissions Ready — Go to Dashboard" else "Continue to Dashboard",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PermissionRowCard(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    text = if (isGranted) "Granted" else "Action Needed",
                    isPositive = isGranted
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onActionClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}
