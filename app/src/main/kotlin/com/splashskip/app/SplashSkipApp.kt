package com.splashskip.app

import android.app.Application
import com.splashskip.app.data.PersistentLogStore
import com.splashskip.app.data.SplashLog

class SplashSkipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SplashLog.attachStore(PersistentLogStore(this))
    }
}
