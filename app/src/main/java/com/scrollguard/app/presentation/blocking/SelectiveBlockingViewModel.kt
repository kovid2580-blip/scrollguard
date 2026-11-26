package com.scrollguard.app.presentation.blocking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollguard.app.blocking.AdapterUiHealth
import com.scrollguard.app.blocking.DetectionRuleRepository
import com.scrollguard.app.data.preferences.ScrollGuardPreferences
import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.domain.model.DetectionConfig
import com.scrollguard.app.domain.model.DiagnosticEvent
import com.scrollguard.app.domain.model.StrictModeState
import com.scrollguard.app.domain.repository.BlockingRuleRepository
import com.scrollguard.app.monitoring.AdapterStatusTracker
import com.scrollguard.app.monitoring.AppAdapterStatus
import com.scrollguard.app.monitoring.DiagnosticsLogBuffer
import com.scrollguard.app.monitoring.LiveDiagnosticsSnapshot
import com.scrollguard.app.security.PinManager
import com.scrollguard.app.security.PinVerificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelectiveRuleStatusUi(
    val rule: BlockingRule,
    val entryPointStatusLabel: String,
    val detectionStatusLabel: String,
    val lastResultSummary: String?,
    val uiNeedsReviewWarning: Boolean
)

data class SelectiveBlockingUiState(
    val builtInRuleItems: List<SelectiveRuleStatusUi> = emptyList(),
    val customRuleItems: List<SelectiveRuleStatusUi> = emptyList(),
    val entryPointSuppressionEnabled: Boolean = true,
    val diagnosticsEnabled: Boolean = false,
    val diagnosticEvents: List<DiagnosticEvent> = emptyList(),
    val liveDiagnostics: LiveDiagnosticsSnapshot = LiveDiagnosticsSnapshot(),
    val strictLocked: Boolean = false,
    val showPinDialog: Boolean = false,
    val pinError: String? = null
)

@HiltViewModel
class SelectiveBlockingViewModel @Inject constructor(
    private val ruleRepository: BlockingRuleRepository,
    private val detectionRuleRepository: DetectionRuleRepository,
    private val preferences: ScrollGuardPreferences,
    private val diagnosticsLogBuffer: DiagnosticsLogBuffer,
    private val adapterStatusTracker: AdapterStatusTracker,
    private val pinManager: PinManager
) : ViewModel() {

    private val showPinDialog = MutableStateFlow(false)
    private val pinError = MutableStateFlow<String?>(null)
    private val sessionUnlocked = MutableStateFlow(false)
    private var pendingAction: (suspend () -> Unit)? = null

    val uiState: StateFlow<SelectiveBlockingUiState> = combine(
        ruleRepository.observeAllRules(),
        preferences.diagnosticsEnabled,
        preferences.entryPointSuppressionEnabled,
        diagnosticsLogBuffer.events,
        adapterStatusTracker.statusByPackage,
        adapterStatusTracker.liveSnapshot,
        preferences.strictModeState,
        showPinDialog,
        pinError,
        sessionUnlocked
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allRules = values[0] as List<BlockingRule>
        val diagnosticsOn = values[1] as Boolean
        val entrySuppressionOn = values[2] as Boolean
        @Suppress("UNCHECKED_CAST")
        val events = values[3] as List<DiagnosticEvent>
        @Suppress("UNCHECKED_CAST")
        val statusMap = values[4] as Map<String, AppAdapterStatus>
        val liveSnapshot = values[5] as LiveDiagnosticsSnapshot
        val strictState = values[6] as StrictModeState
        val pinVisible = values[7] as Boolean
        val error = values[8] as String?
        val unlocked = values[9] as Boolean

        val selective = allRules.filter { it.blockType == BlockType.SELECTIVE_CONTENT }
        val builtIn = selective.filter { it.detectionConfig.isBuiltIn }
            .map { toStatusUi(it, statusMap[it.packageName], entrySuppressionOn) }
        val custom = selective.filter { !it.detectionConfig.isBuiltIn }
            .map { toStatusUi(it, statusMap[it.packageName], entrySuppressionOn) }

        SelectiveBlockingUiState(
            builtInRuleItems = builtIn,
            customRuleItems = custom,
            entryPointSuppressionEnabled = entrySuppressionOn,
            diagnosticsEnabled = diagnosticsOn,
            diagnosticEvents = events,
            liveDiagnostics = liveSnapshot,
            strictLocked = strictState.isSettingsLocked() && !unlocked,
            showPinDialog = pinVisible,
            pinError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SelectiveBlockingUiState()
    )

    private fun toStatusUi(
        rule: BlockingRule,
        status: AppAdapterStatus?,
        entrySuppressionOn: Boolean
    ): SelectiveRuleStatusUi {
        val supportsTabShield = rule.contentType in setOf(
            ContentCategory.INSTAGRAM_REELS,
            ContentCategory.YOUTUBE_SHORTS,
            ContentCategory.FACEBOOK_REELS,
            ContentCategory.SNAPCHAT_SPOTLIGHT
        )
        val entryLabel = when {
            !rule.enabled -> "Entry shield inactive (rule off)"
            !entrySuppressionOn -> "Entry shield disabled (player block active)"
            status != null && status.activeEntryPointSuppressedCount > 0 ->
                "Entry shield active (${status.activeEntryPointSuppressedCount} tab covered)"
            supportsTabShield -> "Nav tab shield ready (shelves use player block)"
            else -> "Player interruption only (dynamic feed)"
        }

        val health = status?.uiHealth ?: AdapterUiHealth.HEALTHY
        val detectionLabel = when {
            !rule.enabled -> "Filtering disabled"
            health == AdapterUiHealth.NEEDS_RULE_REVIEW -> "UI may have changed — review rules"
            health == AdapterUiHealth.LIMITED_VISIBILITY -> "Limited node visibility"
            else -> "Content detection active"
        }

        val lastSummary = status?.let {
            val pct = (it.lastConfidence * 100f).toInt()
            "Last check: ${it.lastDecision} ($pct%)"
        }

        return SelectiveRuleStatusUi(
            rule = rule,
            entryPointStatusLabel = entryLabel,
            detectionStatusLabel = detectionLabel,
            lastResultSummary = lastSummary,
            uiNeedsReviewWarning = rule.enabled && health != AdapterUiHealth.HEALTHY
        )
    }

    fun toggleRule(rule: BlockingRule, enabled: Boolean) {
        runProtectedAction {
            ruleRepository.setRuleEnabled(rule.id, enabled)
            detectionRuleRepository.refreshNow()
        }
    }

    fun setEntryPointSuppressionEnabled(enabled: Boolean) {
        runProtectedAction {
            preferences.setEntryPointSuppressionEnabled(enabled)
        }
    }

    fun saveCustomRule(
        packageName: String,
        appName: String,
        resourceIdsCsv: String,
        contentDescsCsv: String,
        keywordsCsv: String,
        exclusionsCsv: String,
        minConfidence: Float
    ) {
        if (packageName.isBlank()) return
        runProtectedAction {
            val now = System.currentTimeMillis()
            val config = DetectionConfig(
                targetPackage = packageName.trim(),
                category = ContentCategory.CUSTOM_SHORT_FORM,
                resourceIdKeywords = splitCsv(resourceIdsCsv),
                contentDescriptionKeywords = splitCsv(contentDescsCsv),
                textKeywords = splitCsv(keywordsCsv),
                exclusionKeywords = splitCsv(exclusionsCsv),
                minConfidence = minConfidence.coerceIn(0.4f, 0.95f),
                isBuiltIn = false,
                compatibilityNote = "User-defined custom selective rule."
            )
            ruleRepository.upsertRule(
                BlockingRule(
                    packageName = packageName.trim(),
                    appName = appName.trim().ifBlank { packageName.trim() },
                    blockType = BlockType.SELECTIVE_CONTENT,
                    contentType = ContentCategory.CUSTOM_SHORT_FORM,
                    enabled = true,
                    detectionConfig = config,
                    createdAt = now,
                    updatedAt = now
                )
            )
            detectionRuleRepository.refreshNow()
        }
    }

    fun deleteCustomRule(ruleId: Long) {
        runProtectedAction {
            ruleRepository.deleteRule(ruleId)
            detectionRuleRepository.refreshNow()
        }
    }

    fun setDiagnosticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDiagnosticsEnabled(enabled)
        }
    }

    fun clearDiagnostics() {
        diagnosticsLogBuffer.clear()
        adapterStatusTracker.clearLiveCounters()
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            val chars = pin.toCharArray()
            when (val res = pinManager.verifyPin(chars)) {
                PinVerificationResult.Verified, PinVerificationResult.NotSet -> {
                    sessionUnlocked.value = true
                    showPinDialog.value = false
                    pinError.value = null
                    pendingAction?.invoke()
                    pendingAction = null
                }
                is PinVerificationResult.Invalid -> {
                    pinError.value = "Incorrect PIN. ${res.attemptsRemainingBeforeDelay} attempt(s) before delay."
                }
                is PinVerificationResult.LockedOut -> {
                    pinError.value = "Too many attempts. Wait ${res.remainingSeconds}s."
                }
            }
        }
    }

    fun dismissPinDialog() {
        showPinDialog.value = false
        pinError.value = null
        pendingAction = null
    }

    private fun runProtectedAction(action: suspend () -> Unit) {
        if (uiState.value.strictLocked) {
            pendingAction = action
            pinError.value = null
            showPinDialog.value = true
        } else {
            viewModelScope.launch { action() }
        }
    }

    private fun splitCsv(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
