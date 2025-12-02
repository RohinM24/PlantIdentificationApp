package com.example.roleaf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.roleaf.ui.components.LoadingLeaf
import androidx.compose.material3.MaterialTheme

@Composable
fun IdentifyingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LoadingLeaf(modifier = Modifier.size(180.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Identifying your plant…",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.secondary // mint muted text
            )
        }
    }
}





