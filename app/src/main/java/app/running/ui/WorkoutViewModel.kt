package app.running.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.running.BuildConfig
import app.running.data.Backup
import app.running.data.JsonListStore
import app.running.data.ShoePhotos
import app.running.data.UpdateCheck
import app.running.data.UpdateInstaller
import app.running.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class WorkoutViewModel(app: Application) : AndroidViewModel(app) {
    private val workoutStore = JsonListStore(File(app.filesDir, "workouts.json"), Workout.serializer()) { it.id }
    private val shoeStore = JsonListStore(File(app.filesDir, "shoes.json"), Shoe.serializer()) { it.id }
    private val sessionStore = JsonListStore(File(app.filesDir, "sessions.json"), Session.serializer()) { it.id }
    val workouts = workoutStore.items
    val shoes = shoeStore.items
    val sessions = sessionStore.items

    /** Pestaña activa de la pantalla principal. */
    var tab by mutableStateOf(HomeTab.WORKOUTS)

    private val _sessionResult = MutableStateFlow<SessionResult?>(null)
    /** Resultado de la última sesión registrada (mensajes de ánimo y aviso de zapatillas), a la espera de cerrarse. */
    val sessionResult: StateFlow<SessionResult?> = _sessionResult
    fun dismissSessionResult() { _sessionResult.value = null }

    fun saveShoe(shoe: Shoe) {
        val oldPhoto = shoes.value.find { it.id == shoe.id }?.photo
        shoeStore.upsert(shoe)
        // La foto anterior queda huérfana si se ha cambiado o quitado.
        if (oldPhoto != null && oldPhoto != shoe.photo) ShoePhotos.delete(getApplication(), oldPhoto)
    }

    fun deleteShoe(id: String) {
        val photo = shoes.value.find { it.id == id }?.photo
        shoeStore.delete(id)
        ShoePhotos.delete(getApplication(), photo)
    }
    fun deleteSession(id: String) = sessionStore.delete(id)

    fun addSession(session: Session) {
        val shoe = shoes.value.find { it.id == session.shoeId }
        val before = shoe?.totalKm(sessions.value)
        sessionStore.upsert(session)

        var alert: ShoeAlert? = null
        if (shoe != null && before != null && !shoe.retired) {
            val after = shoe.totalKm(sessions.value)
            val reached = after >= shoe.limitKm
            val approaching = before < shoe.limitKm * 0.9 && after >= shoe.limitKm * 0.9
            if (reached || approaching) alert = ShoeAlert(shoe.name, after, shoe.limitKm, reached)
        }

        // Mensajes de ánimo: la sesión, el total acumulado y los km de la zapatilla (que incluyen los previos).
        val totalMeters = sessions.value.sumOf { it.distanceMeters }.toDouble()
        val shoeMeters = shoe?.let { it.totalKm(sessions.value) * 1000 }
        val lines = Cheer.lines(session.distanceMeters.toDouble(), totalMeters, shoeMeters, session.id.hashCode().toLong(), ::trimNumber)
        _sessionResult.value = SessionResult(shoe?.name, lines, alert)
    }

    // ---- Copia de seguridad (exportar / importar)

    private val _message = MutableStateFlow<String?>(null)
    /** Resultado de la última operación, para mostrarlo en un snackbar. */
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    private val _pendingImport = MutableStateFlow<Backup.Contents?>(null)
    /** Copia ya leída y validada, a la espera de que el usuario confirme el reemplazo. */
    val pendingImport: StateFlow<Backup.Contents?> = _pendingImport
    fun cancelImport() { _pendingImport.value = null }

    val appVersion: String = BuildConfig.VERSION_NAME

    fun exportTo(uri: Uri) {
        val app = getApplication<Application>()
        val (w, s, ses) = Triple(workouts.value, shoes.value, sessions.value)
        viewModelScope.launch(Dispatchers.IO) {
            _message.value = runCatching {
                app.contentResolver.openOutputStream(uri, "w")!!.use { Backup.write(app, it, appVersion, w, s, ses) }
                "Copia guardada: ${w.size} entrenos, ${ses.size} sesiones, ${s.size} zapatillas."
            }.getOrElse { "No se pudo guardar la copia: ${it.message ?: "error desconocido"}" }
        }
    }

    /** Lee y valida el archivo; si es correcto queda en [pendingImport] para confirmar. No toca los datos. */
    fun readBackup(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { app.contentResolver.openInputStream(uri)!!.use { Backup.read(it) } }
                .onSuccess { _pendingImport.value = it }
                .onFailure {
                    Log.w("Backup", "No se pudo leer la copia", it)
                    _message.value = (it as? Backup.BackupException)?.message
                        ?: "No se pudo leer el archivo. ¿Es una copia de seguridad de Running?"
                }
        }
    }

    fun confirmImport() {
        val backup = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _message.value = runCatching { restore(backup); "Datos importados." }
                .getOrElse { "No se pudo importar: ${it.message ?: "error desconocido"}" }
        }
    }

    private fun restore(backup: Backup.Contents) {
        val app = getApplication<Application>()
        // Primero las fotos nuevas: si falla algo aquí, los datos actuales siguen intactos.
        backup.photos.forEach { (name, bytes) -> ShoePhotos.file(app, name).writeBytes(bytes) }
        workoutStore.replaceAll(backup.workouts)
        shoeStore.replaceAll(backup.shoes)
        sessionStore.replaceAll(backup.sessions)
        // Las fotos que ya no usa ninguna zapatilla se borran.
        val keep = backup.shoes.mapNotNull { it.photo }.toSet()
        ShoePhotos.dir(app).listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    // ---- Actualizaciones desde GitHub

    private val prefs = app.getSharedPreferences("updates", Context.MODE_PRIVATE)

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update

    private val _updatePrompt = MutableStateFlow<UpdateCheck.Release?>(null)
    /** Versión nueva encontrada por la comprobación automática, para avisar con un diálogo. */
    val updatePrompt: StateFlow<UpdateCheck.Release?> = _updatePrompt
    fun dismissUpdatePrompt() { _updatePrompt.value = null }

    private val _needsInstallPermission = MutableStateFlow(false)
    val needsInstallPermission: StateFlow<Boolean> = _needsInstallPermission
    fun dismissInstallPermission() { _needsInstallPermission.value = false }
    fun openInstallPermissionSettings() {
        _needsInstallPermission.value = false
        UpdateInstaller.openInstallPermissionSettings(getApplication())
    }

    var autoUpdateCheck by mutableStateOf(prefs.getBoolean("autoCheck", true))
        private set
    fun changeAutoUpdateCheck(on: Boolean) {
        autoUpdateCheck = on
        prefs.edit().putBoolean("autoCheck", on).apply()
    }

    private var downloadJob: Job? = null

    /** [manual]: pulsado por el usuario (muestra errores). Si no, es la comprobación silenciosa al abrir la app. */
    fun checkForUpdates(manual: Boolean) {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            try {
                val release = UpdateInstaller.fetchLatest(BuildConfig.UPDATE_URL, allowInsecure = BuildConfig.DEBUG)
                prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
                if (UpdateCheck.isNewer(release.version, appVersion)) {
                    _update.value = UpdateState.Available(release)
                    if (!manual) _updatePrompt.value = release
                } else {
                    _update.value = UpdateState.UpToDate(appVersion)
                }
            } catch (e: UpdateInstaller.UpdateException) {
                _update.value = UpdateState.Idle
                if (manual) _message.value = e.message
            }
        }
    }

    /** Descarga el APK, lo comprueba y abre el instalador del sistema; el usuario confirma allí. */
    fun startUpdate() {
        val release = ((_update.value as? UpdateState.Available)?.release) ?: ((_update.value as? UpdateState.Ready)?.release) ?: return
        val app = getApplication<Application>()
        _updatePrompt.value = null
        if (!UpdateInstaller.canInstall(app)) {
            _needsInstallPermission.value = true
            return
        }
        _update.value = UpdateState.Downloading(release, 0f)
        downloadJob = viewModelScope.launch {
            try {
                var lastPercent = -1
                val file = UpdateInstaller.download(app, release) { p ->
                    val percent = ((p ?: 0f) * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _update.value = UpdateState.Downloading(release, p)
                    }
                }
                UpdateInstaller.checkApk(app, file)
                _update.value = UpdateState.Ready(release, file)
                installDownloaded()
            } catch (e: UpdateInstaller.UpdateException) {
                _update.value = UpdateState.Available(release)
                _message.value = e.message
            }
        }
    }

    fun cancelDownload() {
        val release = (_update.value as? UpdateState.Downloading)?.release ?: return
        downloadJob?.cancel()
        _update.value = UpdateState.Available(release)
    }

    /** Vuelve a abrir el instalador con el APK ya descargado (por si se cerró sin confirmar). */
    fun installDownloaded() {
        val ready = _update.value as? UpdateState.Ready ?: return
        try {
            UpdateInstaller.install(getApplication(), ready.file)
        } catch (e: ActivityNotFoundException) {
            _message.value = "Este teléfono no tiene un instalador de paquetes disponible."
        }
    }

    init {
        // Comprobación silenciosa al abrir la app, como mucho una vez al día.
        val day = 24L * 60 * 60 * 1000
        if (autoUpdateCheck && System.currentTimeMillis() - prefs.getLong("lastCheck", 0L) > day) {
            checkForUpdates(manual = false)
        }
    }

    private val _draft = MutableStateFlow<Workout?>(null)
    /** Entreno que se está editando (null = estamos en la lista). */
    val draft: StateFlow<Workout?> = _draft

    var original: Workout? = null
        private set
    var isNew: Boolean = false
        private set

    fun newWorkout() {
        val w = Workout(name = "", items = emptyList())
        original = w
        isNew = true
        _draft.value = w
    }

    fun edit(w: Workout) {
        original = w
        isNew = false
        _draft.value = w
    }

    fun closeEditor() {
        _draft.value = null
    }

    fun save() {
        val d = _draft.value ?: return
        workoutStore.upsert(d.copy(name = d.name.trim().ifEmpty { "Entreno sin nombre" }))
        _draft.value = null
    }

    fun delete(id: String) = workoutStore.delete(id)
    fun addSamples() = workoutStore.addAll(sampleWorkouts())

    // ---- edición del borrador ----

    private fun update(f: (Workout) -> Workout) = _draft.update { it?.let(f) }

    private fun Workout.mapItem(itemId: String, f: (WorkoutItem) -> WorkoutItem?) =
        copy(items = items.mapNotNull { if (it.id == itemId) f(it) else it })

    fun setName(name: String) = update { it.copy(name = name) }

    fun addSingle(block: Block) = update { it.copy(items = it.items + SingleItem(block = block)) }

    fun addGroup() = update {
        val group = GroupItem(
            repeats = 4,
            blocks = listOf(
                Block(type = BlockType.WORK, end = EndCondition.Distance(400)),
                Block(type = BlockType.RECOVERY, end = EndCondition.Time(60)),
            ),
        )
        it.copy(items = it.items + group)
    }

    fun addToGroup(itemId: String, block: Block) = update { w ->
        w.mapItem(itemId) { if (it is GroupItem) it.copy(blocks = it.blocks + block) else it }
    }

    fun replaceBlock(itemId: String, block: Block) = update { w ->
        w.mapItem(itemId) {
            when (it) {
                is SingleItem -> it.copy(block = block)
                is GroupItem -> it.copy(blocks = it.blocks.map { b -> if (b.id == block.id) block else b })
            }
        }
    }

    fun deleteBlock(itemId: String, blockId: String) = update { w ->
        w.mapItem(itemId) {
            when (it) {
                is SingleItem -> null
                is GroupItem -> it.copy(blocks = it.blocks.filterNot { b -> b.id == blockId }).takeIf { g -> g.blocks.isNotEmpty() }
            }
        }
    }

    fun setRepeats(itemId: String, repeats: Int) = update { w ->
        w.mapItem(itemId) { if (it is GroupItem) it.copy(repeats = repeats.coerceIn(1, 99)) else it }
    }

    fun deleteItem(itemId: String) = update { w -> w.mapItem(itemId) { null } }

    fun moveItem(itemId: String, delta: Int) = update { w ->
        val i = w.items.indexOfFirst { it.id == itemId }
        w.copy(items = w.items.moved(i, delta))
    }

    fun moveBlock(itemId: String, blockId: String, delta: Int) = update { w ->
        w.mapItem(itemId) {
            if (it is GroupItem) it.copy(blocks = it.blocks.moved(it.blocks.indexOfFirst { b -> b.id == blockId }, delta)) else it
        }
    }

    fun duplicateItem(itemId: String) = update { w ->
        val i = w.items.indexOfFirst { it.id == itemId }
        if (i < 0) return@update w
        val copy = when (val it = w.items[i]) {
            is SingleItem -> SingleItem(block = it.block.copy(id = newId()))
            is GroupItem -> GroupItem(repeats = it.repeats, blocks = it.blocks.map { b -> b.copy(id = newId()) })
        }
        w.copy(items = w.items.toMutableList().apply { add(i + 1, copy) })
    }
}

private fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
    val to = index + delta
    if (index !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(index)) }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val release: UpdateCheck.Release) : UpdateState
    /** [progress] 0..1; null si el tamaño es desconocido. */
    data class Downloading(val release: UpdateCheck.Release, val progress: Float?) : UpdateState
    /** APK descargado y verificado, listo para (re)abrir el instalador. */
    data class Ready(val release: UpdateCheck.Release, val file: java.io.File) : UpdateState
}

/** Lo que se enseña tras registrar una sesión. */
data class SessionResult(val shoeName: String?, val lines: List<Cheer.Line>, val alert: ShoeAlert?)
