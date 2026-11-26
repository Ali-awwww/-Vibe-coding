package com.example

import android.app.Application
import com.example.util.configureCrashlytics

class CrashIntelligenceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        configureCrashlytics(this)
    }

    companion object {
        lateinit var instance: CrashIntelligenceApp
            private set
    }
}
