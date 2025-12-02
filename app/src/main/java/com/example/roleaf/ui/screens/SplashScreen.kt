package com.example.roleaf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import com.airbnb.lottie.compose.*
import com.example.roleaf.R
import kotlinx.coroutines.delay
import androidx.compose.material3.MaterialTheme

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lottie_leaf_identifying))
    val progress by animateLottieCompositionAsState(composition, iterations = 1, isPlaying = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "An application by Rohin Maharaj.",
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }

    LaunchedEffect(Unit) {
        delay(2000)
        onTimeout()
    }
}
