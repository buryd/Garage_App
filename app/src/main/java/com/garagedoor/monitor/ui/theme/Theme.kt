package com.garagedoor.monitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.garagedoor.monitor.model.DoorPhase

private val Ink = Color(0xFF1A211C)
private val Paper = Color(0xFFF3F1EB)
private val PaperRaised = Color(0xFFFFFCF7)
private val Night = Color(0xFF101410)
private val NightRaised = Color(0xFF1A221C)
private val NightCard = Color(0xFF232C26)
private val Mist = Color(0xFFB7C4BC)

private val LightColors = lightColorScheme(
    background = Paper,
    surface = PaperRaised,
    surfaceContainer = Color(0xFFE7E4DC),
    surfaceContainerHigh = Color(0xFFDDD9D0),
    primary = Color(0xFF1E3D30),
    onPrimary = Color(0xFFF4FFF8),
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF4E5B54),
    outline = Color(0xFFD5D0C6),
)

private val DarkColors = darkColorScheme(
    background = Night,
    surface = NightRaised,
    surfaceContainer = NightCard,
    surfaceContainerHigh = Color(0xFF2C3830),
    primary = Color(0xFFD5E8DA),
    onPrimary = Color(0xFF143024),
    onSurface = Color(0xFFE7F0EA),
    onSurfaceVariant = Mist,
    outline = Color(0xFF3C4A42),
)

private val GarageType = Typography(
    displayLarge = TextStyle(
        fontSize = 52.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-1).sp,
        lineHeight = 56.sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)

private val GarageShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun GarageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = GarageType,
        shapes = GarageShapes,
        content = content,
    )
}

@Composable
fun statusColor(phase: DoorPhase): Color {
    val dark = isSystemInDarkTheme()
    return when (phase) {
        DoorPhase.Closed -> if (dark) Color(0xFF8FD9A8) else Color(0xFF1F7A45)
        DoorPhase.Open, DoorPhase.Partial -> if (dark) Color(0xFFF0C36A) else Color(0xFF8A5A10)
        DoorPhase.Opening, DoorPhase.Closing -> if (dark) Color(0xFF9EC4F0) else Color(0xFF1D4E89)
        DoorPhase.Offline -> if (dark) Color(0xFFB0B8B4) else Color(0xFF5C6761)
    }
}
