package app.running.ui

import app.running.model.*
import java.util.Locale
import kotlin.math.roundToInt

fun formatPace(secPerKm: Double): String {
    val total = secPerKm.roundToInt()
    return "%d:%02d".format(total / 60, total % 60)
}

fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "%d h %02d min".format(h, m)
        s == 0 -> "$m min"
        m == 0 -> "$s s"
        else -> "%d:%02d min".format(m, s)
    }
}

fun formatDistance(meters: Int): String =
    if (meters < 1000) "$meters m"
    else "%s km".format(trimNumber(meters / 1000.0, 2))

fun trimNumber(v: Double, decimals: Int): String {
    val s = String.format(Locale.getDefault(), "%.${decimals}f", v)
    // Solo se recortan ceros de la parte decimal (700 no puede acabar en 7).
    return if (s.any { it == '.' || it == ',' }) s.trimEnd('0').trimEnd('.', ',') else s
}

fun EndCondition.label(): String = when (this) {
    is EndCondition.Time -> formatDuration(seconds)
    is EndCondition.Distance -> formatDistance(meters)
    EndCondition.Manual -> "Hasta pulsar siguiente"
}

fun BlockTarget.label(): String? = when (kind) {
    TargetKind.NONE -> null
    TargetKind.PACE -> "${formatPace(min)}–${formatPace(max)} /km"
    TargetKind.SPEED -> "${trimNumber(min, 1)}–${trimNumber(max, 1)} km/h"
    TargetKind.HEART_RATE -> "${min.roundToInt()}–${max.roundToInt()} ppm"
}

fun Block.describe(): String = listOfNotNull(end.label(), target.label()).joinToString(" · ")

fun Summary.label(): String {
    val parts = buildList {
        if (seconds > 0) add(formatDuration(seconds))
        if (meters > 0) add(formatDistance(meters))
    }
    if (parts.isEmpty()) return "Sin datos"
    return (if (approx) "≈ " else "") + parts.joinToString(" · ")
}

fun Int.blocksLabel(): String = if (this == 1) "1 bloque" else "$this bloques"
