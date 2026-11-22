package app.running.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.running.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoesScreen(vm: WorkoutViewModel) {
    val shoes by vm.shoes.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Shoe?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Zapatillas") }) },
        bottomBar = { HomeBar(vm) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nueva zapatilla") },
            )
        },
    ) { padding ->
        if (shoes.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Añade tus zapatillas con un límite de km y te avisaremos cuando se acerquen a él.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            val (retired, active) = shoes.partition { it.retired }
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(active, key = { it.id }) { ShoeCard(it, it.totalKm(sessions)) { editing = it } }
                if (retired.isNotEmpty()) {
                    item { Text("Retiradas", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                    items(retired, key = { it.id }) { ShoeCard(it, it.totalKm(sessions)) { editing = it } }
                }
            }
        }
    }

    if (creating) {
        ShoeDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { vm.saveShoe(it); creating = false },
            onDelete = {},
        )
    }
    editing?.let { shoe ->
        ShoeDialog(
            initial = shoe,
            onDismiss = { editing = null },
            onSave = { vm.saveShoe(it); editing = null },
            onDelete = { vm.deleteShoe(shoe.id); editing = null },
        )
    }
}

@Composable
private fun ShoeCard(shoe: Shoe, km: Double, onClick: () -> Unit) {
    val ratio = if (shoe.limitKm > 0) km / shoe.limitKm else 0.0
    val barColor = when {
        ratio >= 1.0 -> MaterialTheme.colorScheme.error
        ratio >= 0.9 -> Color(0xFFF2A93B)
        else -> MaterialTheme.colorScheme.primary
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp).then(if (shoe.retired) Modifier else Modifier)) {
            Text(shoe.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${trimNumber(km, 1)} / ${trimNumber(shoe.limitKm, 0)} km",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = barColor,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    shoe.retired -> "Retirada"
                    ratio >= 1.0 -> "Límite superado por ${trimNumber(km - shoe.limitKm, 1)} km"
                    else -> "Te quedan ${trimNumber(shoe.limitKm - km, 1)} km"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (ratio >= 1.0 && !shoe.retired) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun parseKm(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

@Composable
private fun ShoeDialog(initial: Shoe?, onDismiss: () -> Unit, onSave: (Shoe) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var limit by remember { mutableStateOf(trimNumber(initial?.limitKm ?: 700.0, 1)) }
    var initialKm by remember { mutableStateOf(initial?.initialKm?.let { if (it > 0) trimNumber(it, 1) else "" } ?: "") }
    var retired by remember { mutableStateOf(initial?.retired ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val limitValue = parseKm(limit)?.takeIf { it > 0 }
    val initialValue = if (initialKm.isBlank()) 0.0 else parseKm(initialKm)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva zapatilla" else "Editar zapatilla") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Nombre (marca y modelo)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = limit, onValueChange = { limit = it.filterDecimal() }, label = { Text("Límite de km") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text("Se suele recomendar entre 600 y 800 km") },
                )
                OutlinedTextField(
                    value = initialKm, onValueChange = { initialKm = it.filterDecimal() }, label = { Text("Km previos (opcional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (initial != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Retirada", Modifier.weight(1f))
                        Switch(checked = retired, onCheckedChange = { retired = it })
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Borrar zapatilla", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && limitValue != null && initialValue != null,
                onClick = {
                    onSave(
                        (initial ?: Shoe(name = "", limitKm = 0.0)).copy(
                            name = name.trim(), limitKm = limitValue!!, initialKm = initialValue!!, retired = retired,
                        ),
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar zapatilla?") },
            text = { Text("Las sesiones registradas se conservan, pero quedarán sin zapatilla asignada.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

internal fun String.filterDecimal(): String = filter { it.isDigit() || it == '.' || it == ',' }.take(8)
