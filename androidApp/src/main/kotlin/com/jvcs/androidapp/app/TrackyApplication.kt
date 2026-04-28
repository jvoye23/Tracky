package com.jvcs.androidapp.app

import android.app.Application
import com.jvcs.tracky.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger


class TrackyApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@TrackyApplication)
            androidLogger()
        }
    }
}