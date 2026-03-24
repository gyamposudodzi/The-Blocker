package com.theblocker.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.IOException

class LocalVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val TAG = "LocalVpnService"

    companion object {
        const val ACTION_START = "com.theblocker.app.START_VPN"
        const val ACTION_STOP = "com.theblocker.app.STOP_VPN"
        const val NOTIFICATION_CHANNEL_ID = "vpn_channel"
        
        // CleanBrowsing Adult Filter IPv4
        const val SAFE_DNS_PRIMARY = "185.228.168.10"
        const val SAFE_DNS_SECONDARY = "185.228.169.11"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
            builder.setSession("The Blocker")
            builder.setMtu(1500)

            builder.addAddress("10.0.0.2", 32)
            builder.addDnsServer(SAFE_DNS_PRIMARY)
            builder.addDnsServer(SAFE_DNS_SECONDARY)
            
            // To ensure the system sends traffic through the VPN, we must catch DNS queries.
            // Some systems ignore the VPN if no routes are added.
            // By NOT adding routes to 0.0.0.0/0, we act as a "split tunnel" that only intercepts DNS IF the system respects addDnsServer.
            // For a robust implementation, a real Tun2Socks / packet interceptor is needed,
            // but setting the DNS servers directly is the first step for VpnService.

            vpnInterface = builder.establish()

            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("The Blocker Active")
                .setContentText("Safe browsing protection is currently enabled.")
                .setSmallIcon(android.R.drawable.ic_secure)
                .setOngoing(true)
                .build()

            startForeground(1, notification)
            Log.i(TAG, "VPN Service Started")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopVpn()
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN Service Stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Protection Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
