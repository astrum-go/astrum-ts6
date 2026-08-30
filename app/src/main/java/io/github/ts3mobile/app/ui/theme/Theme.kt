package io.github.ts3mobile.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD4A72C),
    onPrimary = Color(0xFF211A04),
    primaryContainer = Color(0xFF4A3905),
    onPrimaryContainer = Color(0xFFFFE49A),
    secondary = Color(0xFFC9BFA6),
    secondaryContainer = Color(0xFF3A3529),
    tertiary = Color(0xFFD6B0E5),
    tertiaryContainer = Color(0xFF47314E),
    background = Color(0xFF09080D),
    surface = Color(0xFF121017),
    surfaceVariant = Color(0xFF211B27),
    outline = Color(0xFF66586B),
    outlineVariant = Color(0xFF382F3D),
    onSurface = Color(0xFFF1EDE4),
    onSurfaceVariant = Color(0xFFC5BBC8),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5C1517),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val NeverEndTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        ),
        headlineMedium = headlineMedium.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        ),
        headlineSmall = headlineSmall.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        ),
        labelLarge = labelLarge.copy(letterSpacing = 0.8.sp),
        labelMedium = labelMedium.copy(letterSpacing = 1.sp),
        labelSmall = labelSmall.copy(letterSpacing = 1.2.sp),
    )
}

private val NeverEndShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)

@Composable
fun Ts3MobileTheme(content: @Composable () -> Unit) {
    val colors = DarkColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = NeverEndTypography,
        shapes = NeverEndShapes,
        content = content,
    )
}
