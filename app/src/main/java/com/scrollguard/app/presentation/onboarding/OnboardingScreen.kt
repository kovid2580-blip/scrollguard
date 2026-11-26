package com.scrollguard.app.presentation.onboarding

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
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scrollguard.app.presentation.components.ScrollGuardShieldBadge
import com.scrollguard.app.presentation.components.SectionCard

@Composable
fun OnboardingScreen(
    onContinueToPermissions: () -> Unit,
    viewModel: OnboardingViewModel
) {
    var consentChecked by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ScrollGuardShieldBadge(size = 56.dp)
                Column {
                    Text(
                        text = "Welcome to ScrollGuard",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Keep useful features. Block endless short-form feeds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DisclosureItemCard(
                icon = Icons.Rounded.AccessibilityNew,
                title = "Accessibility Service (Digital Wellbeing Guard)",
                body = "ScrollGuard is a digital wellbeing app, not an accessibility aid. With your explicit consent, it inspects UI node identifiers and navigation state on your screen solely to detect Instagram Reels, YouTube Shorts, Facebook Reels, or blocked apps."
            )

            DisclosureItemCard(
                icon = Icons.Rounded.BarChart,
                title = "Usage Access Permission",
                body = "Reads Android's local app foreground usage statistics so ScrollGuard can show accurate daily screen-time reports and enforce optional daily app limits."
            )

            DisclosureItemCard(
                icon = Icons.Rounded.Layers,
                title = "Display Over Other Apps",
                body = "Allows ScrollGuard to present a calm interruption overlay when a blocked feed or restricted app opens, helping you step back intentionally."
            )

            DisclosureItemCard(
                icon = Icons.Rounded.PrivacyTip,
                title = "100% Offline & Private",
                body = "ScrollGuard runs completely offline with no account or backend. Screen content is evaluated transiently in memory and is never uploaded, shared, or persisted."
            )

            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = consentChecked,
                        onCheckedChange = { consentChecked = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I understand how ScrollGuard uses Accessibility, Usage Access, and Overlay permissions locally on my device and consent to configure them.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.completeOnboarding(consentAccepted = true)
                    onContinueToPermissions()
                },
                enabled = consentChecked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Continue to Permission Setup", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DisclosureItemCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    SectionCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
