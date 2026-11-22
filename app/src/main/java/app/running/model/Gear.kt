package app.running.model

import kotlinx.serialization.Serializable

@Serializable
data class Shoe(
    val id: String = newId(),
    val name: String,
    /** Límite de km tras el cual conviene cambiarlas. */
    val limitKm: Double,
    /** Km que ya llevaban antes de empezar a usar la app. */
    val initialKm: Double = 0.0,
    val retired: Boolean = false,
    /** Nombre del archivo de la foto en `filesDir/shoe-photos/` (ver `ShoePhotos`). */
    val photo: String? = null,
)

/** Una sesión realizada. Los km de cada zapatilla se derivan de estas sesiones. */
@Serializable
data class Session(
    val id: String = newId(),
    val epochDay: Long,
    val workoutId: String? = null,
    val workoutName: String? = null,
    val shoeId: String? = null,
    val distanceMeters: Int,
)

fun Shoe.totalKm(sessions: List<Session>): Double =
    initialKm + sessions.filter { it.shoeId == id }.sumOf { it.distanceMeters } / 1000.0

/** Aviso al registrar una sesión: [reached] = límite alcanzado/superado; si no, se acerca (≥90 %). */
data class ShoeAlert(val shoeName: String, val totalKm: Double, val limitKm: Double, val reached: Boolean)
