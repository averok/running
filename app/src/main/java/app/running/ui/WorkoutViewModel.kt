package app.running.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.running.data.JsonListStore
import app.running.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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

    fun saveShoe(shoe: Shoe) = shoeStore.upsert(shoe)
    fun deleteShoe(id: String) = shoeStore.delete(id)
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
