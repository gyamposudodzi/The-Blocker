package com.theblocker.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BlockerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
