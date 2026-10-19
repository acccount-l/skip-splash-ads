package com.example.nodedemo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.nodedemo.service.AccessibilityStatus
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val serviceEnabled = MutableStateFlow(false)
    private val refreshTick = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NodeDemoTheme {
                val enabled by serviceEnabled.collectAsState()
                val tick by refreshTick.collectAsState()
                AppRoot(serviceEnabled = enabled, refreshTick = tick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled.value = AccessibilityStatus.isEnabled(this)
        refreshTick.value = refreshTick.value + 1
    }
}
