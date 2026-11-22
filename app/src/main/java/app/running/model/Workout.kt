package app.running.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.roundToInt

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class BlockType(val label: String) {
    WARMUP("Calentamiento"),
    WORK("Trabajo"),
    RECOVERY("Recuperación"),
    COOLDOWN("Enfriamiento"),
    REST("Descanso"),
}

/** Cuándo termina un bloque. */
@Serializable
sealed interface EndCondition {
    @Serializable @SerialName("time")
    data class Time(val seconds: Int) : EndCondition

    @Serializable @SerialName("distance")
    data class Distance(val meters: Int) : EndCondition

    /** Termina cuando el usuario pulsa "siguiente". */
    @Serializable @SerialName("manual")
    data object Manual : EndCondition
}

enum class TargetKind(val label: String) {
    NONE("Libre"),
    PACE("Ritmo"),
    SPEED("Velocidad"),
    HEART_RATE("Pulsaciones"),
}

/**
 * Objetivo opcional de un bloque. Unidades según [kind]:
 * PACE = segundos por km, SPEED = km/h, HEART_RATE = ppm. Siempre min <= max.
 */
@Serializable
data class BlockTarget(
    val kind: TargetKind = TargetKind.NONE,
    val min: Double = 0.0,
    val max: Double = 0.0,
)

@Serializable
data class Block(
    val id: String = newId(),
    val type: BlockType,
    val end: EndCondition,
    val target: BlockTarget = BlockTarget(),
)

/** Un elemento del entreno: un bloque suelto o un grupo de bloques que se repite. */
@Serializable
sealed interface WorkoutItem {
    val id: String
}

@Serializable @SerialName("single")
data class SingleItem(override val id: String = newId(), val block: Block) : WorkoutItem

@Serializable @SerialName("group")
data class GroupItem(
    override val id: String = newId(),
    val repeats: Int,
    val blocks: List<Block>,
) : WorkoutItem

@Serializable
data class Workout(
    val id: String = newId(),
    val name: String,
    val items: List<WorkoutItem>,
)

/** Lista plana de bloques con las repeticiones desplegadas (útil para el cronograma y para ejecutar). */
fun Workout.expanded(): List<Block> = items.flatMap { item ->
    when (item) {
        is SingleItem -> listOf(item.block)
        is GroupItem -> List(item.repeats) { item.blocks }.flatten()
    }
}

data class Estimate(val seconds: Int?, val meters: Int?, val derived: Boolean)

/** Tiempo y distancia de un bloque; si hay objetivo de ritmo/velocidad se deduce el dato que falta. */
fun Block.estimate(): Estimate {
    val speedMs: Double? = when (target.kind) {
        TargetKind.PACE -> ((target.min + target.max) / 2).takeIf { it > 0 }?.let { 1000.0 / it }
        TargetKind.SPEED -> ((target.min + target.max) / 2).takeIf { it > 0 }?.let { it / 3.6 }
        else -> null
    }
    return when (val e = end) {
        is EndCondition.Time -> Estimate(e.seconds, speedMs?.let { (it * e.seconds).roundToInt() }, speedMs != null)
        is EndCondition.Distance -> Estimate(speedMs?.let { (e.meters / it).roundToInt() }, e.meters, speedMs != null)
        EndCondition.Manual -> Estimate(null, null, false)
    }
}

data class Summary(val seconds: Int, val meters: Int, val approx: Boolean)

fun List<Block>.summary(): Summary {
    var s = 0
    var m = 0
    var approx = false
    for (b in this) {
        val e = b.estimate()
        if (e.seconds == null || e.meters == null || e.derived) approx = true
        s += e.seconds ?: 0
        m += e.meters ?: 0
    }
    return Summary(s, m, approx)
}

/** Peso relativo del bloque para dibujar el cronograma. */
fun Block.timelineWeight(): Float {
    val e = estimate()
    val secs = e.seconds ?: e.meters?.let { it * 0.36 }?.roundToInt() ?: 120
    return secs.coerceAtLeast(10).toFloat()
}
