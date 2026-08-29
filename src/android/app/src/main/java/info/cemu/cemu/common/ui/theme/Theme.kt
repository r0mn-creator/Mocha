package info.cemu.cemu.common.ui.theme

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

private val DarkColorScheme = darkColorScheme()

// "Morning Latte" - Mocha's own palette, not upstream Cemu's. Only 4 tones
// were specified by design (Main/Secondary/Accent/Highlight); the dark
// on-* text color is derived here to keep it legible, not part of the brief.
private val SteamedMilk = Color(0xFFFDFBF7) // Main - base background
private val SweetFoam = Color(0xFFF3EFEA) // Secondary - cards/panels
private val LightCrema = Color(0xFFE4D8CC) // Accent - borders/inactive/dividers
private val ToastedCinnamon = Color(0xFFC8AB8B) // Highlight - active/selected/FAB
private val EspressoText = Color(0xFF4A3B31) // derived - body text on the cream tones

private val LightColorScheme = lightColorScheme(
    background = SteamedMilk,
    onBackground = EspressoText,
    surface = SteamedMilk,
    onSurface = EspressoText,
    surfaceVariant = SweetFoam,
    onSurfaceVariant = EspressoText,
    outline = LightCrema,
    primary = ToastedCinnamon,
    onPrimary = EspressoText,
    primaryContainer = LightCrema,
    onPrimaryContainer = EspressoText,
    secondary = LightCrema,
    onSecondary = EspressoText,
    secondaryContainer = SweetFoam,
    onSecondaryContainer = EspressoText,
)

@Composable
fun CemuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Was true - on Android 12+ that pulls colors from the phone's
    // wallpaper (Material You) instead of ever using the scheme below,
    // which is exactly why "original Cemu" doesn't read as branded at all.
    // Mocha has its own palette now, so it should actually be used.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
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
        content = content
    )
}