package com.example.nodedemo.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDateTime(timeMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))
