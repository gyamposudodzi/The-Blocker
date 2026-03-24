package com.theblocker.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("blocker_settings", Context.MODE_PRIVATE)

    fun getPin(): String? = prefs.getString("app_pin", null)
    
    fun setPin(pin: String?) {
        if (pin == null) {
            prefs.edit().remove("app_pin").apply()
        } else {
            prefs.edit().putString("app_pin", pin).apply()
        }
    }
    
    fun getPartnerEmail(): String? = prefs.getString("partner_email", null)
    
    fun setPartnerEmail(email: String) {
        prefs.edit().putString("partner_email", email).apply()
    }
}
