package app.running.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.running.data.Backup
import app.running.data.JsonListStore
import app.running.data.ShoePhotos
import app.running.model.*
import kotlinx.coroutines.Dispatchers
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

    private val _alert = MutableStateFlow<ShoeAlert?>(null)
    val alert: StateFlow<ShoeAlert?> = _alert
    fun dismissAlert() { _alert.value = null }

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
        if (shoe == null || before == null || shoe.retired) return
        val after = shoe.totalKm(sessions.value)
        val reached = after >= shoe.limitKm
        val approaching = before < shoe.limitKm * 0.9 && after >= shoe.limitKm * 0.9
        if (reached || approaching) _alert.value = ShoeAlert(shoe.name, after, shoe.limitKm, reached)
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

    val appVersion: String
        get() = runCatching {
            val app = getApplication<Application>()
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull() ?: "?"

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
