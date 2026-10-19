package com.example.nodedemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "节点演示 / Node Demo",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text("版本 0.1.0", style = MaterialTheme.typography.bodyMedium)
        Text("Maintained by Project Maintainers", style = MaterialTheme.typography.bodyMedium)
        Text("MIT License", style = MaterialTheme.typography.bodyMedium)

        HorizontalDivider()

        Text(
            text = "免责声明 / Disclaimer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = Disclaimer.chinese,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = Disclaimer.english,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
