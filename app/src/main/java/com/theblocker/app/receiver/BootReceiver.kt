package com.theblocker.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.theblocker.app.vpn.LocalVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val prefs = context.getSharedPreferences("blocker_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("protection_enabled", false)) {
            Log.i(TAG, "Protection was off — skipping VPN restart")
            return
        }

        // User must have previously granted VPN consent; prepare() returns null if still valid.
        if (VpnService.prepare(context) != null) {
            Log.w(TAG, "VPN permission not available after boot — cannot auto-start")
            return
        }

        val vpnIntent = Intent(context, LocalVpnService::class.java).apply {
            action = LocalVpnService.ACTION_START
        }
        try {
            context.startForegroundService(vpnIntent)
            Log.i(TAG, "Requested VPN restart after boot/update")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart VPN after boot", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
