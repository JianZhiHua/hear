package com.qingyi.hear.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8BD9B3),
    onPrimary = Color(0xFF073823),
    primaryContainer = Color(0xFF1F4D3B),
    onPrimaryContainer = Color(0xFFC9F2DD),
    secondary = Color(0xFFE3D2B4),
    onSecondary = Color(0xFF3A2F1B),
    secondaryContainer = Color(0xFF514632),
    onSecondaryContainer = Color(0xFFF6E7C8),
    tertiary = Color(0xFFFFB9A8),
    onTertiary = Color(0xFF542115),
    tertiaryContainer = Color(0xFF74372A),
    onTertiaryContainer = Color(0xFFFFDAD1),
    background = Color(0xFF0F1713),
    onBackground = Color(0xFFE0EAE2),
    surface = Color(0xFF151E19),
    onSurface = Color(0xFFE0EAE2),
    surfaceVariant = Color(0xFF24342C),
    onSurfaceVariant = Color(0xFFC2D0C7),
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFF5FBF7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAF7F0),
    primaryContainer = Color(0xFFD8F2E4),
    secondaryContainer = Color(0xFFF3EBD9),
    tertiaryContainer = Color(0xFFFFD8C7),
    onPrimaryContainer = Color(0xFF063D2D),
    onSecondaryContainer = Color(0xFF443926),
    onTertiaryContainer = Color(0xFF5D2417),

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun HearTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
