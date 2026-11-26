package com.scrollguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollguard.app.data.preferences.ScrollGuardPreferences
import com.scrollguard.app.data.preferences.ThemeMode
import com.scrollguard.app.monitoring.PermissionChecker
import com.scrollguard.app.presentation.navigation.ScrollGuardNavHost
import com.scrollguard.app.presentation.theme.ScrollGuardTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: ScrollGuardPreferences
    @Inject lateinit var permissionChecker: PermissionChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            ScrollGuardTheme(themeMode = themeMode) {
                ScrollGuardNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionChecker.refresh()
    }
}
