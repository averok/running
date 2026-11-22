package app.running.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.running.model.BlockType

// Paleta inspirada en Polar Flow: rojo de marca sobre blanco/gris, y colores de zonas de intensidad.
val PolarRed = Color(0xFFD0002A)
val PolarBlue = Color(0xFF4FC3F0)
val PolarAmber = Color(0xFFFFC72C)
val PolarGreen = Color(0xFF9DBF4A)
val PolarPink = Color(0xFFE0115F)
val PolarGray = Color(0xFFB8BDBF)

private val LightColors = lightColorScheme(
    primary = PolarRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADF),
    onPrimaryContainer = Color(0xFF40000D),
    secondary = Color(0xFF3D3A3F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEDED),
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF0B7FA6),
    background = Color.White,
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF5F5F5F),
    outline = Color(0xFF9A9A9A),
    outlineVariant = Color(0xFFE0E0E0),
    error = Color(0xFFB00020),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF5F5F5),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEDEDED),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF4D6A),
    onPrimary = Color(0xFF40000D),
    primaryContainer = Color(0xFF5C0A1B),
    onPrimaryContainer = Color(0xFFFFDADF),
    secondary = Color(0xFFC9C6CB),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF2E2E32),
    onSecondaryContainer = Color(0xFFEDEDED),
    tertiary = PolarBlue,
    background = Color(0xFF121214),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF121214),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = Color(0xFFB5B5B8),
    outline = Color(0xFF6F6F73),
    outlineVariant = Color(0xFF3A3A3E),
    error = Color(0xFFFF6B6B),
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Color(0xFF1A1A1D),
    surfaceContainer = Color(0xFF1E1E21),
    surfaceContainerHigh = Color(0xFF262629),
    surfaceContainerHighest = Color(0xFF2E2E32),
)

/** Esquinas casi rectas, como en Polar. */
private val PolarShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(6.dp),
)

private val base = Typography()
private val PolarTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
)

/** Cifra grande de resumen (la unidad va aparte, más pequeña). */
val StatNumber = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold)

@Composable
fun RunningTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = PolarShapes,
        typography = PolarTypography,
        content = content,
    )
}

/** Color fijo por tipo de bloque (paleta de zonas de Polar) para que se reconozcan igual en toda la app. */
fun BlockType.color(): Color = when (this) {
    BlockType.WARMUP -> PolarAmber
    BlockType.WORK -> PolarPink
    BlockType.RECOVERY -> PolarGreen
    BlockType.COOLDOWN -> PolarBlue
    BlockType.REST -> PolarGray
}

/** Altura relativa en el cronograma: más alto = más intenso. */
fun BlockType.intensity(): Float = when (this) {
    BlockType.WORK -> 1f
    BlockType.WARMUP, BlockType.COOLDOWN -> 0.65f
    BlockType.RECOVERY -> 0.4f
    BlockType.REST -> 0.2f
}
