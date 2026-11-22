package app.running.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

            SectionHeader("Actualizaciones")
            UpdateSection(vm)

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

@Composable
private fun UpdateSection(vm: WorkoutViewModel) {
    val state by vm.update.collectAsStateWithLifecycle()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val s = state) {
            UpdateState.Idle -> {}
            UpdateState.Checking -> {
                Text("Buscando actualizaciones…", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is UpdateState.UpToDate -> Text("Tienes la última versión (${s.version}).", style = MaterialTheme.typography.bodyMedium)
            is UpdateState.Available -> {
                Text("Hay una versión nueva: ${s.release.version}", style = MaterialTheme.typography.titleMedium)
                if (s.release.apkSize > 0) {
                    Text(
                        "%.1f MB".format(s.release.apkSize / 1_048_576.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (s.release.notes.isNotEmpty()) {
                    Text(s.release.notes.take(600), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = vm::startUpdate, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Text("Descargar e instalar")
                }
            }
            is UpdateState.Downloading -> {
                Text("Descargando la versión ${s.release.version}…", style = MaterialTheme.typography.bodyMedium)
                val p = s.progress
                if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), gapSize = 0.dp, drawStopIndicator = {})
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = vm::cancelDownload, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Text("Cancelar")
                }
            }
            is UpdateState.Ready -> {
                Text("La versión ${s.release.version} está descargada y verificada.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = vm::installDownloaded, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Text("Instalar")
                }
            }
        }
        if (state !is UpdateState.Checking && state !is UpdateState.Downloading) {
            OutlinedButton(
                onClick = { vm.checkForUpdates(manual = true) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) { Text("Buscar actualizaciones") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Buscar al abrir la app (1 vez al día)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = vm.autoUpdateCheck, onCheckedChange = vm::changeAutoUpdateCheck)
        }
        Text(
            "La app solo se conecta a github.com para buscar y descargar versiones nuevas. " +
                "Android te pedirá confirmar la instalación.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Diálogos globales de actualización: aviso de versión nueva y petición del permiso de instalación. */
@Composable
fun UpdateDialogs(vm: WorkoutViewModel) {
    val prompt by vm.updatePrompt.collectAsStateWithLifecycle()
    val needsPermission by vm.needsInstallPermission.collectAsStateWithLifecycle()

    prompt?.let { release ->
        AlertDialog(
            onDismissRequest = vm::dismissUpdatePrompt,
            title = { Text("Nueva versión ${release.version}") },
            text = { Text(release.notes.take(400).ifEmpty { "Hay una actualización disponible." }) },
            confirmButton = {
                TextButton(onClick = { vm.tab = HomeTab.SETTINGS; vm.startUpdate() }) { Text("Actualizar") }
            },
            dismissButton = { TextButton(onClick = vm::dismissUpdatePrompt) { Text("Más tarde") } },
        )
    }

    if (needsPermission) {
        AlertDialog(
            onDismissRequest = vm::dismissInstallPermission,
            title = { Text("Permitir instalar actualizaciones") },
            text = {
                Text(
                    "Android necesita tu permiso para que Running pueda instalar su propia actualización. " +
                        "Pulsa «Abrir ajustes», activa «Permitir desde esta fuente», vuelve atrás y pulsa de nuevo «Descargar e instalar».",
                )
            },
            confirmButton = { TextButton(onClick = vm::openInstallPermissionSettings) { Text("Abrir ajustes") } },
            dismissButton = { TextButton(onClick = vm::dismissInstallPermission) { Text("Cancelar") } },
        )
    }
}
