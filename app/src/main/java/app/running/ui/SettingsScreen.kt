package app.running.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: WorkoutViewModel) {
    val message by vm.message.collectAsStateWithLifecycle()
    val pending by vm.pendingImport.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val shoes by vm.shoes.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    // Se admite cualquier tipo: según el proveedor, un ZIP puede llegar con tipos distintos. Ya se valida al leerlo.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readBackup(uri)
    }

    Scaffold(
        topBar = { PolarTopBar("Ajustes") },
        bottomBar = { HomeBar(vm) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Copia de seguridad")
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Guarda tus entrenos, sesiones, zapatillas y fotos en un único archivo ZIP. " +
                        "Sirve para pasar tus datos a otro teléfono.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Ahora mismo: ${workouts.size} entrenos · ${sessions.size} sesiones · ${shoes.size} zapatillas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { exportLauncher.launch("running-backup-${LocalDate.now()}.zip") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("Exportar datos") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("Importar datos") }
                Text(
                    "Al importar se reemplazan todos los datos actuales por los de la copia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionHeader("Acerca de")
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Running ${vm.appVersion}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Proyecto personal · github.com/averok/running",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    pending?.let { backup ->
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text("¿Importar esta copia?") },
            text = {
                Text(
                    "La copia tiene ${backup.workouts.size} entrenos, ${backup.sessions.size} sesiones y " +
                        "${backup.shoes.size} zapatillas.\n\n" +
                        "Se reemplazarán tus datos actuales (${workouts.size} entrenos, ${sessions.size} sesiones, " +
                        "${shoes.size} zapatillas). Esta acción no se puede deshacer.",
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmImport) { Text("Reemplazar") } },
            dismissButton = { TextButton(onClick = vm::cancelImport) { Text("Cancelar") } },
        )
    }
}
