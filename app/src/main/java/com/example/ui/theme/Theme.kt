package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.example.ui.viewmodel.MusicTheme

private val GoldenOrangeColorScheme = darkColorScheme(
    primary = GoldenOrangePrimary,
    primaryContainer = GoldenOrangePrimaryContainer,
    secondary = GoldenOrangeSecondary,
    background = GoldenOrangeBackground,
    surface = GoldenOrangeSurface,
    onBackground = GoldenOrangeOnSurface,
    onSurface = GoldenOrangeOnSurface,
    surfaceVariant = GoldenOrangeSurface,
    onSurfaceVariant = GoldenOrangeOnSurface.copy(alpha = 0.7f)
)

private val WarmBronzeColorScheme = darkColorScheme(
    primary = WarmBronzePrimary,
    primaryContainer = WarmBronzePrimaryContainer,
    secondary = WarmBronzeSecondary,
    background = WarmBronzeBackground,
    surface = WarmBronzeSurface,
    onBackground = WarmBronzeOnSurface,
    onSurface = WarmBronzeOnSurface,
    surfaceVariant = WarmBronzeSurface,
    onSurfaceVariant = WarmBronzeOnSurface.copy(alpha = 0.7f)
)

private val SunburstYellowColorScheme = darkColorScheme(
    primary = SunburstYellowPrimary,
    primaryContainer = SunburstYellowPrimaryContainer,
    secondary = SunburstYellowSecondary,
    background = SunburstYellowBackground,
    surface = SunburstYellowSurface,
    onBackground = SunburstYellowOnSurface,
    onSurface = SunburstYellowOnSurface,
    surfaceVariant = SunburstYellowSurface,
    onSurfaceVariant = SunburstYellowOnSurface.copy(alpha = 0.7f)
)

private val CozyAmberColorScheme = darkColorScheme(
    primary = CozyAmberPrimary,
    primaryContainer = CozyAmberPrimaryContainer,
    secondary = CozyAmberSecondary,
    background = CozyAmberBackground,
    surface = CozyAmberSurface,
    onBackground = CozyAmberOnSurface,
    onSurface = CozyAmberOnSurface,
    surfaceVariant = CozyAmberSurface,
    onSurfaceVariant = CozyAmberOnSurface.copy(alpha = 0.7f)
)

@Composable
fun GMusicTheme(
    musicTheme: MusicTheme = MusicTheme.GOLDEN_ORANGE,
    content: @Composable () -> Unit
) {
    val colorScheme = when (musicTheme) {
        MusicTheme.GOLDEN_ORANGE -> GoldenOrangeColorScheme
        MusicTheme.WARM_BRONZE -> WarmBronzeColorScheme
        MusicTheme.SUNBURST_YELLOW -> SunburstYellowColorScheme
        MusicTheme.COZY_AMBER -> CozyAmberColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
