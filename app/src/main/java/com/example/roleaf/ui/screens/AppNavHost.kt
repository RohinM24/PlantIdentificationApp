package com.example.roleaf.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.example.roleaf.ui.viewmodel.MainViewModel
import com.example.roleaf.ui.viewmodel.ScreenState

/**
 * Central host that renders the correct screen based on MainViewModel.ui's screen state.
 *
 * This project already stores the current screen in the ViewModel (ScreenState). Instead of
 * using a NavGraph driven by navigation actions inside each screen, we render the desired
 * screen based on that state. This is the minimal, non-invasive fix to resolve the
 * unresolved reference in MainActivity and keep your current screen implementations.
 */
@Composable
fun AppNavHost(viewModel: MainViewModel) {
    val uiState by viewModel.ui.collectAsState()

    // Some of your screens expect a NavHostController param (HomeScreen/ResultScreen).
    // Provide one here so those function signatures are satisfied.
    val navController = rememberNavController()

    when (val screen = uiState.screen) {
        is ScreenState.Splash -> {
            // SplashScreen will call onTimeout when it wants to move on
            SplashScreen(onTimeout = { viewModel.setScreen(ScreenState.Input) })
        }

        is ScreenState.Input -> {
            // Initial / input screen where the user adds photos etc.
            InitialScreen(viewModel = viewModel)
        }

        is ScreenState.Loading -> {
            // Simple "identifying" / loading indicator screen
            IdentifyingScreen()
        }

        is ScreenState.Result -> {
            // ResultScreen expects a NavHostController and the ViewModel.
            // The screen can call viewModel.resetToInput() or other functions on the VM.
            ResultScreen(navController = navController, viewModel = viewModel)
        }

        is ScreenState.Splash /* explicit */ -> {
            // already handled above; kept for clarity
            SplashScreen(onTimeout = { viewModel.setScreen(ScreenState.Input) })
        }

        // default fallback to input screen
        else -> {
            InitialScreen(viewModel = viewModel)
        }
    }
}
