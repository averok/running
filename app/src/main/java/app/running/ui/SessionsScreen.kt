package app.running.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.running.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.ZoneOffset
import kotlin.math.roundToInt

private fun dayLabel(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(vm: WorkoutViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val shoes by vm.shoes.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Session?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sesiones") }) },
        bottomBar = { HomeBar(vm) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Registrar sesión") },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Registra tus salidas para llevar el control de km y del desgaste de tus zapatillas.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sessions.sortedByDescending { it.epochDay }, key = { it.id }) { s ->
                    val shoeName = shoes.find { it.id == s.shoeId }?.name
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.workoutName ?: "Carrera libre", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${dayLabel(s.epochDay)} · ${formatDistance(s.distanceMeters)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    shoeName ?: "Sin zapatillas",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { toDelete = s }) { Icon(Icons.Default.Delete, "Borrar") }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        SessionDialog(
            workouts = workouts,
            shoes = shoes.filter { !it.retired },
            defaultShoeId = sessions.maxByOrNull { it.epochDay }?.shoeId
                ?.takeIf { id -> shoes.any { it.id == id && !it.retired } }
                ?: shoes.firstOrNull { !it.retired }?.id,
            onDismiss = { showDialog = false },
            onConfirm = { vm.addSession(it); showDialog = false },
        )
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar sesión?") },
            text = { Text("Los km se restarán de las zapatillas asignadas.") },
            confirmButton = { TextButton(onClick = { vm.deleteSession(s.id); toDelete = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDialog(
    workouts: List<Workout>,
    shoes: List<Shoe>,
    defaultShoeId: String?,
    onDismiss: () -> Unit,
    onConfirm: (Session) -> Unit,
) {
    var epochDay by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var workout by remember { mutableStateOf<Workout?>(null) }
    var shoeId by remember { mutableStateOf(defaultShoeId) }
    var distText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val km = distText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar sesión") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, Modifier.fillMaxWidth()) {
                    Text("Fecha: ${dayLabel(epochDay)}")
                }
                Picker(
                    label = "Entreno",
                    selected = workout?.name ?: "Carrera libre",
                    options = listOf<Pair<String, Workout?>>("Carrera libre" to null) + workouts.map { it.name to it },
                    onSelect = { w ->
                        workout = w
                        w?.expanded()?.summary()?.meters?.takeIf { it > 0 }?.let { distText = trimNumber(it / 1000.0, 2) }
                    },
                )
                OutlinedTextField(
                    value = distText, onValueChange = { distText = it.filterDecimal() },
                    label = { Text("Distancia (km)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Picker(
                    label = "Zapatillas",
                    selected = shoes.find { it.id == shoeId }?.name ?: "Sin zapatillas",
                    options = listOf<Pair<String, String?>>("Sin zapatillas" to null) + shoes.map { it.name to it.id },
                    onSelect = { shoeId = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = km != null,
                onClick = {
                    onConfirm(
                        Session(
                            epochDay = epochDay,
                            workoutId = workout?.id,
                            workoutName = workout?.name,
                            shoeId = shoeId,
                            distanceMeters = (km!! * 1000).roundToInt(),
                        ),
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDay * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { epochDay = Math.floorDiv(it, 86_400_000L) }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun <T> Picker(label: String, selected: String, options: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) {
            Text("$label: $selected", Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}
