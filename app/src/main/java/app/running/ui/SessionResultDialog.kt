package app.running.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.running.model.Cheer
import app.running.model.ShoeAlert
import kotlin.math.roundToInt

/** Diálogo tras registrar una sesión: mensajes de ánimo y, si toca, el aviso de límite de zapatillas. */
@Composable
fun SessionResultDialog(vm: WorkoutViewModel) {
    val result by vm.sessionResult.collectAsStateWithLifecycle()
    val r = result ?: return
    AlertDialog(
        onDismissRequest = vm::dismissSessionResult,
        title = { Text("¡Sesión guardada!") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                r.lines.forEach { line -> CheerBlock(label(line.slot, r.shoeName), line) }
                r.alert?.let { ShoeAlertBlock(it) }
            }
        },
        confirmButton = { TextButton(onClick = vm::dismissSessionResult) { Text("¡Genial!") } },
    )
}

private fun label(slot: Cheer.Slot, shoeName: String?) = when (slot) {
    Cheer.Slot.SESSION -> "Esta sesión"
    Cheer.Slot.TOTAL -> "Total acumulado"
    Cheer.Slot.SHOES -> shoeName ?: "Zapatillas"
}

@Composable
private fun CheerBlock(label: String, line: Cheer.Line) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatDistance(line.meters.roundToInt()), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        line.comparison?.let { Text("Has corrido ${it.text}.", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun ShoeAlertBlock(a: ShoeAlert) {
    val total = trimNumber(a.totalKm, 1)
    val limit = trimNumber(a.limitKm, 0)
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = if (a.reached) MaterialTheme.colorScheme.error else PolarAmber)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                if (a.reached) "Límite de km alcanzado" else "Zapatillas casi al límite",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                if (a.reached) "${a.shoeName} lleva $total km y su límite era $limit km. Es hora de plantearse cambiarlas."
                else "${a.shoeName} lleva $total de $limit km. Te quedan ${trimNumber(a.limitKm - a.totalKm, 1)} km.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
