package com.scrollguard.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollguard.app.blocking.DetectionRuleRepository
import com.scrollguard.app.data.preferences.ScrollGuardPreferences
import com.scrollguard.app.data.preferences.ThemeMode
import com.scrollguard.app.domain.model.PermissionStatus
import com.scrollguard.app.domain.usecase.BackupRestoreUseCase
import com.scrollguard.app.domain.usecase.ExportReportsUseCase
import com.scrollguard.app.monitoring.PermissionChecker
import com.scrollguard.app.scheduling.ScheduleAlarmScheduler
import com.scrollguard.app.security.PinActionResult
import com.scrollguard.app.security.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hasPinSet: Boolean = false,
    val permissionStatus: PermissionStatus = PermissionStatus(false, false, false, true),
    val exportedText: String? = null,
    val exportTitle: String? = null,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: ScrollGuardPreferences,
    private val pinManager: PinManager,
    private val permissionChecker: PermissionChecker,
    private val exportReportsUseCase: ExportReportsUseCase,
    private val backupRestoreUseCase: BackupRestoreUseCase,
    private val detectionRuleRepository: DetectionRuleRepository,
    private val scheduleAlarmScheduler: ScheduleAlarmScheduler
) : ViewModel() {

    private val exportedText = MutableStateFlow<String?>(null)
    private val exportTitle = MutableStateFlow<String?>(null)
    private val feedbackMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.themeMode,
        pinManager.hasPinSet,
        permissionChecker.status,
        exportedText,
        exportTitle,
        feedbackMessage,
        errorMessage
    ) { values ->
        SettingsUiState(
            themeMode = values[0] as ThemeMode,
            hasPinSet = values[1] as Boolean,
            permissionStatus = values[2] as PermissionStatus,
            exportedText = values[3] as String?,
            exportTitle = values[4] as String?,
            feedbackMessage = values[5] as String?,
            errorMessage = values[6] as String?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(permissionStatus = permissionChecker.readCurrentStatus())
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    fun changePin(currentPin: String, newPin: String, confirmPin: String) {
        viewModelScope.launch {
            clearMessages()
            when (
                val res = pinManager.changePin(
                    currentPin.toCharArray(),
                    newPin.toCharArray(),
                    confirmPin.toCharArray()
                )
            ) {
                is PinActionResult.Success -> feedbackMessage.value = "PIN updated successfully."
                is PinActionResult.Error -> errorMessage.value = res.message
            }
        }
    }

    fun exportCsvReports() {
        viewModelScope.launch {
            clearMessages()
            val csv = exportReportsUseCase.exportRecentReportsCsv()
            exportTitle.value = "ScrollGuard Usage Report (CSV)"
            exportedText.value = csv
        }
    }

    fun exportBackupJson() {
        viewModelScope.launch {
            clearMessages()
            val json = backupRestoreUseCase.exportConfigurationJson()
            exportTitle.value = "ScrollGuard Rules & Schedules Backup (JSON)"
            exportedText.value = json
        }
    }

    fun restoreBackupJson(rawJson: String) {
        viewModelScope.launch {
            clearMessages()
            backupRestoreUseCase.importConfigurationJson(rawJson)
                .onSuccess { (rulesCount, schedulesCount) ->
                    detectionRuleRepository.refreshNow()
                    scheduleAlarmScheduler.rescheduleNextBoundary()
                    feedbackMessage.value = "Restored $rulesCount rule(s) and $schedulesCount schedule(s)."
                }
                .onFailure {
                    errorMessage.value = "Invalid backup JSON: ${it.localizedMessage ?: "parse error"}"
                }
        }
    }

    fun clearExportPreview() {
        exportedText.value = null
        exportTitle.value = null
    }

    fun refreshPermissions() {
        permissionChecker.refresh()
    }

    private fun clearMessages() {
        feedbackMessage.value = null
        errorMessage.value = null
    }
}
