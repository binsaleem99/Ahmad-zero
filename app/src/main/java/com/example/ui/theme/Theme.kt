package com.zero.crm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeumorphicColorScheme = lightColorScheme(
    primary = BauhausRed,
    onPrimary = Color.White,
    secondary = BauhausMediumGray,
    onSecondary = BauhausWhite,
    background = BauhausBlack,
    onBackground = BauhausWhite,
    surface = BauhausDarkGray,
    onSurface = BauhausWhite,
    surfaceVariant = BauhausGridLine,
    onSurfaceVariant = BauhausLightGray,
    error = BauhausRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Force neumorphic light mode by default
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = NeumorphicColorScheme,
        typography = Typography,
        content = content
    )
}
