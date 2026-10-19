package com.example.nodedemo.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.nodedemo.R

/**
 * 自带演示页。页面上的 demo_target_button 是无障碍服务唯一允许操作的目标。
 */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)

        val status = findViewById<TextView>(R.id.demo_status)
        findViewById<Button>(R.id.demo_target_button).setOnClickListener {
            status.text = getString(R.string.demo_status_clicked)
        }
    }
}
