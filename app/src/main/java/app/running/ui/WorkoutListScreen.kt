package app.running.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.running.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutListScreen(vm: WorkoutViewModel) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Workout?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mis entrenos") }) },
        bottomBar = { HomeBar(vm) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = vm::newWorkout,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nuevo entreno") },
            )
        },
    ) { padding ->
        if (workouts.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Todavía no tienes entrenos.\nCrea uno con bloques de tiempo, distancia o ritmo.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = vm::addSamples) { Text("Cargar ejemplos") }
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(workouts, key = { it.id }) { w ->
                    val blocks = w.expanded()
                    Card(Modifier.fillMaxWidth().clickable { vm.edit(w) }) {
                        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(w.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${blocks.size} bloques · ${blocks.summary().label()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { toDelete = w }) { Icon(Icons.Default.Delete, "Borrar") }
                            }
                            Spacer(Modifier.height(8.dp))
                            Timeline(blocks, Modifier.padding(end = 8.dp))
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar entreno?") },
            text = { Text("Se borrará \"${w.name}\".") },
            confirmButton = { TextButton(onClick = { vm.delete(w.id); toDelete = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}
