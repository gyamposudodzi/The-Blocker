package com.theblocker.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class BlockerAccessibilityService : AccessibilityService() {

    private val TAG = "BlockerAccService"
    
    // Keywords to watch out for to prevent uninstallation or VPN/Accessibility disable
    private val forbiddenKeywords = listOf(
        "the blocker",
        "uninstall",
        "clear data",
        "force stop",
        "vpn",
        "accessibility"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val packageName = event.packageName?.toString() ?: ""
            val className = event.className?.toString() ?: ""
            val textBlocks = event.text.joinToString(" ").lowercase()

            // We primarily care about the user being in the Settings app or Package Installer
            if (packageName == "com.android.settings" || packageName == "com.google.android.packageinstaller") {
                
                // Check if they are looking at our app or critical system settings
                val isTryingToTamper = forbiddenKeywords.any { textBlocks.contains(it) }
                                       
                if (isTryingToTamper) {
                    Log.i(TAG, "Tamper attempt detected! Forcing back.")
                    // Perform global back action to kick user out of the vulnerable settings screen
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.packageNames = arrayOf("com.android.settings", "com.google.android.packageinstaller")
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.DEFAULT or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        
        this.serviceInfo = info
        Log.i(TAG, "Accessibility Service Connected")
    }
}
