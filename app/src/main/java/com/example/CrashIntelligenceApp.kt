package com.example

import android.app.Application

class CrashIntelligenceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.util.ensureFirebaseInitialized(this)
    }

    companion object {
        lateinit var instance: CrashIntelligenceApp
            private set
    }
}
