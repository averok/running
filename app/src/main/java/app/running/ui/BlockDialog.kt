package app.running.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.running.model.*
import kotlin.math.roundToInt

private fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

private fun paceSeconds(min: String, sec: String): Double? {
    val m = min.trim().toIntOrNull() ?: return null
    val s = sec.trim().ifEmpty { "0" }.toIntOrNull() ?: return null
    return (m * 60 + s).toDouble().takeIf { it > 0 && s < 60 }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockDialog(initial: Block?, onDismiss: () -> Unit, onConfirm: (Block) -> Unit) {
    val base = initial ?: Block(type = BlockType.WORK, end = EndCondition.Time(60))

    var type by remember { mutableStateOf(base.type) }

    // condición de fin
    var endKind by remember {
        mutableIntStateOf(when (base.end) { is EndCondition.Time -> 0; is EndCondition.Distance -> 1; EndCondition.Manual -> 2 })
    }
    var minutes by remember { mutableStateOf((base.end as? EndCondition.Time)?.let { (it.seconds / 60).toString() } ?: "") }
    var seconds by remember { mutableStateOf((base.end as? EndCondition.Time)?.let { (it.seconds % 60).toString() } ?: "") }
    val dist0 = (base.end as? EndCondition.Distance)?.meters
    var km by remember { mutableStateOf(dist0 != null && dist0 % 1000 == 0) }
    var distText by remember {
        mutableStateOf(dist0?.let { if (it % 1000 == 0) (it / 1000).toString() else it.toString() } ?: "")
    }

    // objetivo
    var tKind by remember { mutableStateOf(base.target.kind) }
    val t = base.target
    var loM by remember { mutableStateOf(if (t.kind == TargetKind.PACE) (t.min.toInt() / 60).toString() else "") }
    var loS by remember { mutableStateOf(if (t.kind == TargetKind.PACE) "%02d".format(t.min.toInt() % 60) else "") }
    var hiM by remember { mutableStateOf(if (t.kind == TargetKind.PACE) (t.max.toInt() / 60).toString() else "") }
    var hiS by remember { mutableStateOf(if (t.kind == TargetKind.PACE) "%02d".format(t.max.toInt() % 60) else "") }
    var loT by remember { mutableStateOf(if (t.kind == TargetKind.SPEED || t.kind == TargetKind.HEART_RATE) trimNumber(t.min, 1) else "") }
    var hiT by remember { mutableStateOf(if (t.kind == TargetKind.SPEED || t.kind == TargetKind.HEART_RATE) trimNumber(t.max, 1) else "") }

    val end: EndCondition? = when (endKind) {
        0 -> {
            val total = (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
            if (total > 0) EndCondition.Time(total) else null
        }
        1 -> parseNumber(distText)?.let { v ->
            val m = (if (km) v * 1000 else v).roundToInt()
            if (m > 0) EndCondition.Distance(m) else null
        }
        else -> EndCondition.Manual
    }

    val target: BlockTarget? = when (tKind) {
        TargetKind.NONE -> BlockTarget()
        TargetKind.PACE -> {
            val a = paceSeconds(loM, loS)
            val b = paceSeconds(hiM, hiS)
            if (a != null && b != null) BlockTarget(tKind, minOf(a, b), maxOf(a, b)) else null
        }
        else -> {
            val a = parseNumber(loT)
            val b = parseNumber(hiT)
            if (a != null && b != null && a > 0 && b > 0) BlockTarget(tKind, minOf(a, b), maxOf(a, b)) else null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo bloque" else "Editar bloque") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tipo", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockType.entries.forEach {
                        FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it.label) })
                    }
                }

                Text("Termina por", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Tiempo", "Distancia", "Manual").forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = endKind == i,
                            onClick = { endKind = i },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
                when (endKind) {
                    0 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumField(minutes, { minutes = it }, "min", Modifier.weight(1f))
                        NumField(seconds, { seconds = it }, "s", Modifier.weight(1f))
                    }
                    1 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumField(distText, { distText = it }, if (km) "km" else "metros", Modifier.weight(1f), decimal = true)
                        FilterChip(selected = !km, onClick = { km = false }, label = { Text("m") })
                        FilterChip(selected = km, onClick = { km = true }, label = { Text("km") })
                    }
                    else -> Text(
                        "El bloque dura hasta que pulses \"siguiente\" durante el entreno.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text("Objetivo", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetKind.entries.forEach {
                        FilterChip(selected = tKind == it, onClick = { tKind = it }, label = { Text(it.label) })
                    }
                }
                when (tKind) {
                    TargetKind.NONE -> {}
                    TargetKind.PACE -> {
                        PaceRow("Más rápido (min/km)", loM, { loM = it }, loS, { loS = it })
                        PaceRow("Más lento (min/km)", hiM, { hiM = it }, hiS, { hiS = it })
                    }
                    else -> {
                        val unit = if (tKind == TargetKind.SPEED) "km/h" else "ppm"
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField(loT, { loT = it }, "Mínimo ($unit)", Modifier.weight(1f), decimal = true)
                            NumField(hiT, { hiT = it }, "Máximo ($unit)", Modifier.weight(1f), decimal = true)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = end != null && target != null,
                onClick = { onConfirm(base.copy(type = type, end = end!!, target = target!!)) },
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PaceRow(label: String, m: String, onM: (String) -> Unit, s: String, onS: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumField(m, onM, "min", Modifier.weight(1f))
            Text(":")
            NumField(s, onS, "s", Modifier.weight(1f))
        }
    }
}

@Composable
private fun NumField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onChange(new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(7)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
