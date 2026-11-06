package com.mazidulhaque.kokhon

import android.app.Application

/**
 * Application entry point. Dependencies are wired by hand through [AppContainer]
 * instead of a DI framework: the graph is small, and this keeps builds fast.
 */
class KokhonApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
