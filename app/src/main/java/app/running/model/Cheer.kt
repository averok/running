package app.running.model

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mensajes de ánimo: compara una distancia con referencias conocidas (ciudades, edificios, montañas…) para
 * decir cosas como «has corrido el equivalente a 3 portaviones» o «más que la altura del Everest».
 *
 * Lógica pura (sin Android) para poder probarla con tests unitarios. El texto que devuelve [compare] continúa
 * la frase «Has corrido …».
 *
 * Las distancias entre ciudades NO están escritas a mano: se calculan (círculo máximo) a partir de coordenadas,
 * y por eso son siempre «en línea recta». Las demás medidas son fijas y están verificadas (ver comentarios).
 */
object Cheer {
    sealed interface Ref {
        val id: String
        val meters: Double
    }

    /** Algo que se puede contar: «el equivalente a 3 portaviones». [single] es la frase para ~1 unidad. */
    data class Countable(
        override val id: String,
        override val meters: Double,
        val single: String,
        val plural: String,
    ) : Ref

    /** Una distancia o medida con nombre propio: «2,4 veces la altura del Everest». */
    data class Named(override val id: String, override val meters: Double, val nom: String) : Ref

    data class Comparison(val refId: String, val text: String)

    enum class Slot { SESSION, TOTAL, SHOES }

    data class Line(val slot: Slot, val meters: Double, val comparison: Comparison?)

    // ---------------------------------------------------------------- referencias

    private const val EARTH_RADIUS_M = 6_371_008.8

    internal fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    private class City(val lat: Double, val lon: Double)

    private val cities = mapOf(
        "Barcelona" to City(41.3874, 2.1686), "Girona" to City(41.9794, 2.8214), "Tarragona" to City(41.1189, 1.2445),
        "Valencia" to City(39.4699, -0.3763), "Madrid" to City(40.4168, -3.7038), "Sevilla" to City(37.3891, -5.9845),
        "Bilbao" to City(43.2630, -2.9350), "Zaragoza" to City(41.6488, -0.8891), "Palma" to City(39.5696, 2.6502),
        "A Coruña" to City(43.3623, -8.4115), "Lisboa" to City(38.7223, -9.1393), "París" to City(48.8566, 2.3522),
        "Londres" to City(51.5074, -0.1278), "Roma" to City(41.9028, 12.4964), "Berlín" to City(52.5200, 13.4050),
        "Nueva York" to City(40.7128, -74.0060), "Tokio" to City(35.6762, 139.6503), "Sídney" to City(-33.8688, 151.2093),
        "Buenos Aires" to City(-34.6037, -58.3816), "Mataró" to City(41.5381, 2.4447), "Sitges" to City(41.2372, 1.8058),
        "Manresa" to City(41.7250, 1.8265), "Lleida" to City(41.6176, 0.6200), "Perpiñán" to City(42.6887, 2.8948),
        "Moscú" to City(55.7558, 37.6173), "Ciudad de México" to City(19.4326, -99.1332),
    )

    private val cityPairs = listOf(
        "Barcelona" to "Mataró", "Barcelona" to "Sitges", "Barcelona" to "Manresa", "Barcelona" to "Girona",
        "Barcelona" to "Tarragona", "Barcelona" to "Lleida", "Barcelona" to "Perpiñán", "Barcelona" to "Palma",
        "Barcelona" to "Zaragoza", "Barcelona" to "Valencia", "Madrid" to "Bilbao", "Madrid" to "Sevilla",
        "Madrid" to "Lisboa", "Madrid" to "Barcelona", "Barcelona" to "París", "Sevilla" to "Barcelona",
        "Barcelona" to "Roma", "A Coruña" to "Barcelona", "Barcelona" to "Londres", "Barcelona" to "Berlín",
        "Barcelona" to "Moscú", "Londres" to "Nueva York", "Madrid" to "Nueva York", "Madrid" to "Ciudad de México",
        "Madrid" to "Buenos Aires", "Madrid" to "Tokio", "Barcelona" to "Sídney",
    )

    private fun route(a: String, b: String): Named {
        val ca = cities.getValue(a)
        val cb = cities.getValue(b)
        return Named(
            id = "ruta:$a-$b",
            meters = haversineMeters(ca.lat, ca.lon, cb.lat, cb.lon),
            nom = "la distancia en línea recta entre $a y $b",
        )
    }

    /**
     * Medidas fijas (comprobadas en fuentes en nov. 2026): Everest 8.848,86 m (oficial, 2020); Mont Blanc 4.807 m
     * (medición de oct. 2026, varía cada año con la nieve); Teide 3.715 m (IGN; muchas webs repiten 3.718); Burj
     * Khalifa 828 m (altura arquitectónica); Golden Gate 2.737 m; Titanic 269 m; Estrecho de Gibraltar ~14 km (las
     * fuentes dan 13–14,3); Canal de la Mancha ~33 km en su punto más estrecho; Camino Francés ~780 km (las fuentes
     * dan 770–790); Transiberiano 9.289 km; ecuador 40.075 km; Tierra–Luna 384.400 km; portaviones clase Nimitz
     * 333 m; torre Eiffel 330 m (con antenas); campo de fútbol 105 m (medida internacional recomendada);
     * maratón 42,195 km; pista de atletismo 400 m.
     */
    internal val refs: List<Ref> = buildList {
        cityPairs.forEach { (a, b) -> add(route(a, b)) }
        add(Named("obj:everest", 8_849.0, "la altura del Everest"))
        add(Named("obj:montblanc", 4_807.0, "la altura del Mont Blanc"))
        add(Named("obj:teide", 3_715.0, "la altura del Teide"))
        add(Named("obj:burj", 828.0, "la altura del Burj Khalifa"))
        add(Named("obj:goldengate", 2_737.0, "la longitud del puente Golden Gate"))
        add(Named("obj:titanic", 269.0, "la longitud del Titanic"))
        add(Named("ruta:gibraltar", 14_000.0, "la anchura del Estrecho de Gibraltar en su punto más estrecho"))
        add(Named("ruta:mancha", 33_000.0, "la anchura del Canal de la Mancha en su punto más estrecho"))
        add(Named("ruta:camino", 780_000.0, "el Camino de Santiago (Camino Francés)"))
        add(Named("ruta:transiberiano", 9_289_000.0, "el Transiberiano de Moscú a Vladivostok"))
        add(Named("ruta:ecuador", 40_075_000.0, "una vuelta al mundo por el ecuador"))
        add(Named("ruta:luna", 384_400_000.0, "la distancia de la Tierra a la Luna"))
        add(Countable("cnt:portaviones", 333.0, "la longitud de un portaviones clase Nimitz", "portaviones clase Nimitz"))
        add(Countable("cnt:eiffel", 330.0, "la altura de la torre Eiffel", "torres Eiffel apiladas"))
        add(Countable("cnt:campo", 105.0, "el largo de un campo de fútbol", "campos de fútbol, uno tras otro"))
        add(Countable("cnt:maraton", 42_195.0, "la distancia de un maratón (42,2 km)", "maratones"))
        add(Countable("cnt:pista", 400.0, "una vuelta a una pista de atletismo", "vueltas a una pista de atletismo"))
    }

    // ---------------------------------------------------------------- selección

    /** Por debajo de esto no se comenta nada (sería ridículo comparar 30 m con un portaviones). */
    private const val MIN_METERS = 50.0

    /** Cuántos candidatos (los mejor encajados) entran en el sorteo; así no sale siempre la misma referencia. */
    private const val POOL = 6

    /** null si la referencia no encaja con esa distancia. Menor puntuación = mejor encaje. */
    private fun score(ref: Ref, meters: Double): Double? {
        val r = meters / ref.meters
        return when (ref) {
            is Named -> if (r in 0.5..8.0) abs(ln(r)) else null
            // Los «contables» encajan bien aunque salgan unas decenas de unidades; se prefiere en torno a 4.
            is Countable -> if (r in 0.8..150.0) abs(ln(r / 4.0)) * 0.6 else null
        }
    }

    /**
     * Elige una referencia para [meters] y devuelve la frase que sigue a «Has corrido». [seed] hace la elección
     * estable (la misma sesión da siempre el mismo mensaje); [avoid] son referencias ya usadas en otras líneas.
     * Devuelve null si la distancia es demasiado pequeña o no es un número válido.
     * [fmt] formatea un número con N decimales como se quiera mostrar (p. ej. con coma).
     */
    fun compare(meters: Double, seed: Long, avoid: Set<String> = emptySet(), fmt: (Double, Int) -> String): Comparison? {
        if (!(meters >= MIN_METERS) || meters.isInfinite()) return null
        val scored = refs.mapNotNull { ref -> score(ref, meters)?.let { ref to it } }.sortedBy { it.second }
        val pool = scored.filter { it.first.id !in avoid }.ifEmpty { scored }.take(POOL)
        if (pool.isEmpty()) return null
        val pick = pool[Math.floorMod(seed, pool.size.toLong()).toInt()].first
        return Comparison(pick.id, phrase(pick, meters, fmt))
    }

    /** «de la distancia…» a partir de «la distancia…» (y «del …» a partir de «el …»). */
    internal fun genitive(nom: String): String = when {
        nom.startsWith("el ") -> "del " + nom.removePrefix("el ")
        nom.startsWith("la ") -> "de la " + nom.removePrefix("la ")
        nom.startsWith("una ") -> "de una " + nom.removePrefix("una ")
        nom.startsWith("un ") -> "de un " + nom.removePrefix("un ")
        else -> "de $nom"
    }

    private fun count(x: Double, fmt: (Double, Int) -> String): String =
        if (x >= 10 || abs(x - x.roundToInt()) < 0.05) x.roundToInt().toString() else fmt(x, 1)

    /** La frase para [meters] respecto a [ref], según cuánto la supere (o se quede corta). */
    internal fun phrase(ref: Ref, meters: Double, fmt: (Double, Int) -> String): String {
        val r = meters / ref.meters
        val nom = when (ref) {
            is Named -> ref.nom
            is Countable -> ref.single
        }
        return when {
            r < 0.8 -> "el ${(r * 100).roundToInt()} % ${genitive(nom)}"
            r < 0.97 -> "casi $nom"
            r <= 1.05 -> "más o menos $nom"
            r < 1.5 -> "más que $nom"
            ref is Countable -> "el equivalente a ${count(r, fmt)} ${ref.plural}"
            else -> "${count(r, fmt)} veces $nom"
        }
    }

    /**
     * Las líneas de ánimo tras una sesión: la propia sesión, el total acumulado y (si hay zapatilla) sus km.
     * Cada línea usa una referencia distinta.
     */
    fun lines(
        sessionMeters: Double,
        totalMeters: Double,
        shoeMeters: Double?,
        seed: Long,
        fmt: (Double, Int) -> String,
    ): List<Line> {
        val used = mutableSetOf<String>()
        val inputs = buildList {
            add(Slot.SESSION to sessionMeters)
            add(Slot.TOTAL to totalMeters)
            if (shoeMeters != null) add(Slot.SHOES to shoeMeters)
        }
        return inputs.map { (slot, meters) ->
            val c = compare(meters, seed + slot.ordinal * 7919L, used, fmt)
            if (c != null) used += c.refId
            Line(slot, meters, c)
        }
    }
}
