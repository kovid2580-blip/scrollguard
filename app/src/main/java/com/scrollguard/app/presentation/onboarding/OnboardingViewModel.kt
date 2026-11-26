package com.scrollguard.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollguard.app.data.preferences.ScrollGuardPreferences
import com.scrollguard.app.domain.model.PermissionStatus
import com.scrollguard.app.monitoring.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: ScrollGuardPreferences,
    val permissionChecker: PermissionChecker
) : ViewModel() {

    val permissionStatus: StateFlow<PermissionStatus> = permissionChecker.status
    val onboardingCompleted: StateFlow<Boolean> = preferences.onboardingCompleted.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    fun refreshPermissions() {
        permissionChecker.refresh()
    }

    fun completeOnboarding(consentAccepted: Boolean) {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(
                completed = true,
                consentAccepted = consentAccepted
            )
        }
    }
}
