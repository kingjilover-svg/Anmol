package com.example

import android.app.Application
import android.content.Context
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer

class UnknownAiApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as UnknownAiApplication).container
