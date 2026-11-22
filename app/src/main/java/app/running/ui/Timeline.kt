package app.running.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.running.model.Block
import app.running.model.timelineWeight

/** Cronograma: un segmento por bloque, ancho = duración estimada, alto = intensidad. */
@Composable
fun Timeline(blocks: List<Block>, modifier: Modifier = Modifier, height: Int = 36) {
    Row(
        modifier = modifier.fillMaxWidth().height(height.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        blocks.forEach { b ->
            Box(
                Modifier
                    .weight(b.timelineWeight())
                    .fillMaxHeight(b.type.intensity())
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .background(b.type.color()),
            )
        }
    }
}
