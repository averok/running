package app.running.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.running.model.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as DateTextStyle
import kotlin.math.roundToInt

private fun dayLabel(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(EsLocale))

private val longDayFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", EsLocale)

private fun monthIndex(m: YearMonth) = m.year * 12 + m.monthValue - 1
private fun monthOf(index: Int) = YearMonth.of(index / 12, index % 12 + 1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(vm: WorkoutViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val shoes by vm.shoes.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Session?>(null) }

    val today = LocalDate.now().toEpochDay()
    var monthIdx by rememberSaveable { mutableIntStateOf(monthIndex(YearMonth.now())) }
    var selected by rememberSaveable { mutableStateOf<Long?>(today) }
    val month = monthOf(monthIdx)

    val monthSessions = sessions.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }
    val metersByDay = monthSessions.groupBy { it.epochDay }.mapValues { (_, l) -> l.sumOf { it.distanceMeters } }
    val shown = (selected?.let { d -> monthSessions.filter { it.epochDay == d } } ?: monthSessions)
        .sortedByDescending { it.epochDay }

    Scaffold(
        topBar = { PolarTopBar("Historial") },
        bottomBar = { HomeBar(vm) },
        floatingActionButton = { PolarFab("Registrar sesión", Icons.Default.Add, { showDialog = true }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthIdx--; selected = null }) { Icon(Icons.Default.ChevronLeft, "Mes anterior") }
                    Text(
                        month.month.getDisplayName(DateTextStyle.FULL, EsLocale).uppercase(EsLocale) + " " + month.year,
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (month != YearMonth.now() || selected != today) {
                        TextButton(onClick = { monthIdx = monthIndex(YearMonth.now()); selected = today }) { Text("HOY") }
                    }
                    IconButton(onClick = { monthIdx++; selected = null }) { Icon(Icons.Default.ChevronRight, "Mes siguiente") }
                }
            }
            item {
                MonthCalendar(
                    month = month,
                    metersByDay = metersByDay,
                    selected = selected,
                    today = today,
                    onSelect = { d -> selected = if (selected == d) null else d },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item { MonthSummary(monthSessions, Modifier.padding(top = 12.dp)) }
            item {
                SectionHeader(
                    selected?.let { LocalDate.ofEpochDay(it).format(longDayFormat).replaceFirstChar { c -> c.uppercase() } }
                        ?: "Sesiones de ${month.month.getDisplayName(DateTextStyle.FULL, EsLocale)}",
                )
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (selected != null) "Sin sesiones este día." else "Sin sesiones este mes.",
                        Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(shown, key = { it.id }) { s ->
                    SessionCard(
                        s,
                        shoes.find { it.id == s.shoeId }?.name,
                        onDelete = { toDelete = s },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
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
            initialEpochDay = selected ?: today,
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

/** Bloque rojo con los totales del mes, como el resumen de Polar. */
@Composable
private fun MonthSummary(monthSessions: List<Session>, modifier: Modifier = Modifier) {
    val onRed = MaterialTheme.colorScheme.onPrimary
    val dim = onRed.copy(alpha = 0.85f)
    val meters = monthSessions.sumOf { it.distanceMeters }
    val count = monthSessions.size
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatBlock(trimNumber(meters / 1000.0, 1), "km", "Distancia", valueColor = onRed, labelColor = dim)
        StatBlock(count.toString(), null, "Sesiones", valueColor = onRed, labelColor = dim)
        StatBlock(
            if (count > 0) trimNumber(meters / 1000.0 / count, 1) else "–",
            if (count > 0) "km" else null,
            "Media",
            valueColor = onRed,
            labelColor = dim,
        )
    }
}

@Composable
private fun SessionCard(s: Session, shoeName: String?, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    PolarCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.workoutName ?: "Carrera libre", style = MaterialTheme.typography.titleMedium)
                Text(dayLabel(s.epochDay), style = MaterialTheme.typography.bodyMedium)
                Text(
                    shoeName ?: "Sin zapatillas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val km = s.distanceMeters >= 1000
            StatBlock(
                value = if (km) trimNumber(s.distanceMeters / 1000.0, 2) else s.distanceMeters.toString(),
                unit = if (km) "km" else "m",
                label = "Distancia",
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Borrar") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDialog(
    workouts: List<Workout>,
    shoes: List<Shoe>,
    defaultShoeId: String?,
    initialEpochDay: Long,
    onDismiss: () -> Unit,
    onConfirm: (Session) -> Unit,
) {
    var epochDay by remember { mutableLongStateOf(initialEpochDay) }
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
                OutlinedButton(onClick = { showDatePicker = true }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
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
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
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
