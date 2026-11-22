package app.running.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.running.model.BlockType

@Composable
fun RunningTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Color fijo por tipo de bloque para que se reconozcan igual en toda la app. */
fun BlockType.color(): Color = when (this) {
    BlockType.WARMUP -> Color(0xFFF2A93B)
    BlockType.WORK -> Color(0xFFE5484D)
    BlockType.RECOVERY -> Color(0xFF30A46C)
    BlockType.COOLDOWN -> Color(0xFF3E63DD)
    BlockType.REST -> Color(0xFF8B8D98)
}

/** Altura relativa en el cronograma: más alto = más intenso. */
fun BlockType.intensity(): Float = when (this) {
    BlockType.WORK -> 1f
    BlockType.WARMUP, BlockType.COOLDOWN -> 0.65f
    BlockType.RECOVERY -> 0.4f
    BlockType.REST -> 0.2f
}
