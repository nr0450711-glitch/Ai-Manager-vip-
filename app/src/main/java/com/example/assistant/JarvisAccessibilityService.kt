package com.example.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AccessibilityBridgeState {
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
    }
}

class JarvisAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridgeState.setEnabled(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Explicit-consent minimal event observer; does not capture window content or passwords.
    }

    override fun onInterrupt() {
        AccessibilityBridgeState.setEnabled(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        AccessibilityBridgeState.setEnabled(false)
    }
}
