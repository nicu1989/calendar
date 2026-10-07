package ro.calendarortodox.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Light = lightColorScheme(
    primary = Color(0xFF285648), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EAE2), onPrimaryContainer = Color(0xFF173C30),
    secondary = Color(0xFF806137), secondaryContainer = Color(0xFFF2E7D1),
    background = Color(0xFFF8F6F0), surface = Color(0xFFF8F6F0),
    surfaceContainer = Color(0xFFF0EEE6), surfaceContainerLow = Color(0xFFFFFDF7),
    onSurface = Color(0xFF25332D), onSurfaceVariant = Color(0xFF5F6A63),
    outlineVariant = Color(0xFFDFE2D7), error = Color(0xFFA1333A),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA5D2BB), onPrimary = Color(0xFF163B2D),
    primaryContainer = Color(0xFF244C3D), secondary = Color(0xFFE4C18B),
    background = Color(0xFF151D19), surface = Color(0xFF151D19),
    surfaceContainer = Color(0xFF222D26), surfaceContainerLow = Color(0xFF1B2520),
    onSurface = Color(0xFFE5E8DE), onSurfaceVariant = Color(0xFFB5C0B5), error = Color(0xFFFFADB0),
)

@Composable
fun CalendarTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
