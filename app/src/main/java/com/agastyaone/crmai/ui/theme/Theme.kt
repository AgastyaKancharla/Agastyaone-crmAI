package com.agastyaone.crmai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TealPrimary = Color(0xFF0F6E5E)
private val OrangeSecondary = Color(0xFFE07A2C)

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    secondary = OrangeSecondary,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5DBFA9),
    secondary = OrangeSecondary,
)

@Composable
fun AgastyaOneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
