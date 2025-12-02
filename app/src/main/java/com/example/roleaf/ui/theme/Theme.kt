package com.example.roleaf.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme

private val LightColors = lightColorScheme(
    primary = RoleafPrimary,
    onPrimary = RoleafOnPrimary,
    primaryContainer = RoleafPrimaryVariant,
    secondary = RoleafAccent,
    onSecondary = RoleafOnPrimary,
    background = RoleafBackground,
    surface = RoleafSurface,
    onBackground = RoleafStrongText,
    onSurface = RoleafStrongText,
    surfaceVariant = RoleafSurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = RoleafPrimary,
    onPrimary = RoleafOnPrimary,
    primaryContainer = RoleafPrimaryVariant,
    secondary = RoleafAccent,
    onSecondary = RoleafOnPrimary,
    background = RoleafBackground,
    surface = RoleafSurface,
    onBackground = RoleafStrongText,
    onSurface = RoleafStrongText,
    surfaceVariant = RoleafSurfaceVariant,
)

@Composable
fun RoleafTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(), // keep default or wire your custom Typography
        shapes = Shapes(),
        content = content
    )
}


