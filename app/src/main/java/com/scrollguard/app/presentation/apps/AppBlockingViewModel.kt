package com.scrollguard.app.presentation.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollguard.app.blocking.DetectionRuleRepository
import com.scrollguard.app.data.preferences.ScrollGuardPreferences
import com.scrollguard.app.domain.model.BlockType
import com.scrollguard.app.domain.model.BlockingRule
import com.scrollguard.app.domain.model.ContentCategory
import com.scrollguard.app.domain.model.InstalledApp
import com.scrollguard.app.domain.model.StrictModeState
import com.scrollguard.app.domain.repository.BlockingRuleRepository
import com.scrollguard.app.domain.repository.InstalledAppsRepository
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

data class AppBlockingItemUi(
    val app: InstalledApp,
    val fullBlockEnabled: Boolean,
    val dailyLimitMinutes: Int?,
    val selectiveRuleActive: Boolean
)

data class AppBlockingUiState(
    val items: List<AppBlockingItemUi> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val strictLocked: Boolean = false,
    val showPinDialog: Boolean = false,
    val pinError: String? = null
)

@HiltViewModel
class AppBlockingViewModel @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val ruleRepository: BlockingRuleRepository,
    private val detectionRuleRepository: DetectionRuleRepository,
    private val preferences: ScrollGuardPreferences,
    private val pinManager: PinManager
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    private val loading = MutableStateFlow(true)
    private val showPinDialog = MutableStateFlow(false)
    private val pinError = MutableStateFlow<String?>(null)
    private val sessionUnlocked = MutableStateFlow(false)

    private var pendingAction: (suspend () -> Unit)? = null

    val uiState: StateFlow<AppBlockingUiState> = combine(
        installedApps,
        ruleRepository.observeAllRules(),
        searchQuery,
        loading,
        preferences.strictModeState,
        showPinDialog,
        pinError,
        sessionUnlocked
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val apps = values[0] as List<InstalledApp>
        @Suppress("UNCHECKED_CAST")
        val rules = values[1] as List<BlockingRule>
        val query = values[2] as String
        val isLoading = values[3] as Boolean
        val strictState = values[4] as StrictModeState
        val pinVisible = values[5] as Boolean
        val error = values[6] as String?
        val unlocked = values[7] as Boolean

        val fullRulesByPkg = rules
            .filter { it.blockType == BlockType.FULL_APP }
            .associateBy { it.packageName }
        val selectiveActivePkgs = rules
            .filter { it.blockType == BlockType.SELECTIVE_CONTENT && it.enabled }
            .map { it.packageName }
            .toSet()

        val filtered = apps.filter {
            query.isBlank() ||
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }.map { app ->
            val fullRule = fullRulesByPkg[app.packageName]
            AppBlockingItemUi(
                app = app,
                fullBlockEnabled = fullRule?.enabled == true,
                dailyLimitMinutes = fullRule?.dailyLimitMinutes,
                selectiveRuleActive = app.packageName in selectiveActivePkgs
            )
        }

        AppBlockingUiState(
            items = filtered,
            searchQuery = query,
            isLoading = isLoading,
            strictLocked = strictState.isSettingsLocked() && !unlocked,
            showPinDialog = pinVisible,
            pinError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppBlockingUiState()
    )

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            loading.value = true
            installedApps.value = installedAppsRepository.getLaunchableApps()
            loading.value = false
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun toggleFullBlock(app: InstalledApp, enabled: Boolean) {
        runProtectedAction {
            val existing = ruleRepository.getRulesForPackage(app.packageName)
                .firstOrNull { it.blockType == BlockType.FULL_APP }
            val now = System.currentTimeMillis()
            val updated = existing?.copy(enabled = enabled, updatedAt = now)
                ?: BlockingRule(
                    packageName = app.packageName,
                    appName = app.appName,
                    blockType = BlockType.FULL_APP,
                    contentType = ContentCategory.FULL_APP,
                    enabled = enabled,
                    dailyLimitMinutes = null,
                    createdAt = now,
                    updatedAt = now
                )
            ruleRepository.upsertRule(updated)
            detectionRuleRepository.refreshNow()
        }
    }

    fun setDailyLimit(app: InstalledApp, limitMinutes: Int?) {
        runProtectedAction {
            val existing = ruleRepository.getRulesForPackage(app.packageName)
                .firstOrNull { it.blockType == BlockType.FULL_APP }
            val now = System.currentTimeMillis()
            val updated = existing?.copy(
                enabled = limitMinutes != null || existing.enabled,
                dailyLimitMinutes = limitMinutes,
                updatedAt = now
            ) ?: BlockingRule(
                packageName = app.packageName,
                appName = app.appName,
                blockType = BlockType.FULL_APP,
                contentType = ContentCategory.FULL_APP,
                enabled = limitMinutes != null,
                dailyLimitMinutes = limitMinutes,
                createdAt = now,
                updatedAt = now
            )
            ruleRepository.upsertRule(updated)
            detectionRuleRepository.refreshNow()
        }
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
}
