package com.theblocker.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.theblocker.app.vpn.LocalVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            val vpnIntent = Intent(context, LocalVpnService::class.java).apply {
                action = LocalVpnService.ACTION_START
            }
            try {
                // Must ensure we have the VpnService prepared, but startForegroundService handles exceptions if not.
                context.startForegroundService(vpnIntent)
            } catch (e: Exception) {
                // Log and fail gracefully if permission was revoked
            }
        }
    }
}
