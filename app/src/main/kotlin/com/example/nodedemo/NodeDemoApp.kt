package com.example.nodedemo

import android.app.Application
import com.example.nodedemo.data.DemoLog
import com.example.nodedemo.data.PersistentLogStore

class NodeDemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DemoLog.attachStore(PersistentLogStore(this))
    }
}
