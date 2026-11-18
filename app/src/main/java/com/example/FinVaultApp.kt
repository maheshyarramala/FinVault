package com.example

import android.app.Application
import com.example.core.di.AppContainer

class FinVaultApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
