package com.theblocker.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.IOException
import java.net.InetAddress

class LocalVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var forwarder: DnsVpnForwarder? = null

    companion object {
        const val ACTION_START = "com.theblocker.app.START_VPN"
        const val ACTION_STOP = "com.theblocker.app.STOP_VPN"
        const val NOTIFICATION_CHANNEL_ID = "vpn_channel"
        private const val TAG = "LocalVpnService"

        // Local DNS endpoint advertised to the system; we forward queries upstream.
        private const val VPN_DNS = "10.0.0.1"
        private const val VPN_ADDRESS = "10.0.0.2"

        // CleanBrowsing Adult Filter IPv4
        const val SAFE_DNS_PRIMARY = "185.228.168.10"
        const val SAFE_DNS_SECONDARY = "185.228.169.11"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            setProtectionPreference(false)
            stopVpn()
            return START_NOT_STICKY
        }

        setProtectionPreference(true)
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .setSession("The Blocker")
                .setMtu(1500)
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(VPN_DNS)
                // Only DNS traffic enters the TUN; everything else bypasses normally.
                .addRoute(VPN_DNS, 32)
                .setBlocking(true)

            // Prefer IPv4 so apps cannot bypass filtering via IPv6 DNS.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.allowFamily(OsConstants.AF_INET)
            }

            val established = builder.establish()
            if (established == null) {
                Log.e(TAG, "VPN establish() returned null — permission may have been revoked")
                setProtectionPreference(false)
                stopSelf()
                return
            }
            vpnInterface = established

            val upstream = listOf(
                InetAddress.getByName(SAFE_DNS_PRIMARY),
                InetAddress.getByName(SAFE_DNS_SECONDARY)
            )
            forwarder = DnsVpnForwarder(this, established, upstream).also { it.start() }
            isRunning = true

            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("The Blocker Active")
                .setContentText("Safe browsing protection is currently enabled.")
                .setSmallIcon(android.R.drawable.ic_secure)
                .setOngoing(true)
                .build()

            ServiceCompat.startForeground(
                this,
                1,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            )
            Log.i(TAG, "VPN Service Started with DNS forwarder")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            setProtectionPreference(false)
            stopVpn()
        }
    }

    private fun stopVpn() {
        forwarder?.stop()
        forwarder = null
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN Service Stopped")
    }

    override fun onDestroy() {
        forwarder?.stop()
        forwarder = null
        try {
            vpnInterface?.close()
        } catch (_: IOException) {
        }
        vpnInterface = null
        isRunning = false
        super.onDestroy()
    }

    private fun setProtectionPreference(enabled: Boolean) {
        getSharedPreferences("blocker_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("protection_enabled", enabled)
            .apply()
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
