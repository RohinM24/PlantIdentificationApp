package com.example.roleaf.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.roleaf.R

@Composable
fun LoadingLeaf(modifier: Modifier = Modifier.size(140.dp)) {
    // rememberLottieComposition returns a wrapper; use .value to get LottieComposition?
    val compositionResult = rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_leaf_identifying)
    )
    val composition = compositionResult.value

    // animateLottieCompositionAsState returns a LottieAnimationState (do NOT delegate with 'by')
    val lottieState = animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = true
    )

    // Use named 'progress' lambda if supported; otherwise use positional lambda.
    // Preferred (named lambda) — should work in 6.x:
    LottieAnimation(
        composition = composition,
        progress = { lottieState.progress },
        modifier = modifier
    )

    // If you still get "Cannot find parameter 'progress'", use positional form:
    // LottieAnimation(composition, { lottieState.progress }, modifier = modifier)
}



