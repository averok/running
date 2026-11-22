package app.running.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

class CheerTest {
    private val fmt: (Double, Int) -> String = { v, d -> "%.${d}f".format(Locale.ROOT, v).replace('.', ',') }

    private fun ref(id: String) = Cheer.refs.first { it.id == id }
    private val everest get() = ref("obj:everest")                // 8.849 m
    private val portaviones get() = ref("cnt:portaviones")        // 333 m

    @Test fun refs_tienen_ids_unicos_y_medidas_positivas() {
        assertEquals(Cheer.refs.size, Cheer.refs.map { it.id }.toSet().size)
        assertTrue(Cheer.refs.all { it.meters > 0 && it.meters.isFinite() })
    }

    @Test fun distancias_entre_ciudades_calculadas_coinciden_con_valores_conocidos() {
        fun km(id: String) = ref(id).meters / 1000
        // Valores de referencia (línea recta) contrastados aparte; tolerancia 1 %.
        assertEquals(505.0, km("ruta:Madrid-Barcelona"), 505.0 * 0.01)
        assertEquals(5570.0, km("ruta:Londres-Nueva York"), 5570.0 * 0.01)
        assertEquals(10_762.0, km("ruta:Madrid-Tokio"), 10_762.0 * 0.01)
        assertEquals(85.3, km("ruta:Barcelona-Girona"), 85.3 * 0.01) // ojo: NO 76 km, que era un recuerdo erróneo
    }

    @Test fun haversine_basica() {
        assertEquals(0.0, Cheer.haversineMeters(41.0, 2.0, 41.0, 2.0), 1e-6)
        // Un grado de latitud ≈ 111,2 km
        assertEquals(111_195.0, Cheer.haversineMeters(0.0, 0.0, 1.0, 0.0), 200.0)
    }

    @Test fun frase_para_cosas_con_nombre_segun_cuanto_se_supera() {
        val m = everest.meters
        assertEquals("el 60 % de la altura del Everest", Cheer.phrase(everest, m * 0.6, fmt))
        assertEquals("casi la altura del Everest", Cheer.phrase(everest, m * 0.9, fmt))
        assertEquals("más o menos la altura del Everest", Cheer.phrase(everest, m * 1.0, fmt))
        assertEquals("más que la altura del Everest", Cheer.phrase(everest, m * 1.2, fmt))
        assertEquals("2,4 veces la altura del Everest", Cheer.phrase(everest, m * 2.4, fmt))
        assertEquals("2 veces la altura del Everest", Cheer.phrase(everest, m * 2.0, fmt))
        assertEquals("12 veces la altura del Everest", Cheer.phrase(everest, m * 12.3, fmt))
    }

    @Test fun frase_para_cosas_contables_el_ejemplo_del_usuario() {
        // «has corrido el equivalente a 3 portaviones»
        assertEquals("el equivalente a 3 portaviones clase Nimitz", Cheer.phrase(portaviones, 999.0, fmt))
        assertEquals("el equivalente a 2,5 portaviones clase Nimitz", Cheer.phrase(portaviones, 333.0 * 2.5, fmt))
        assertEquals("más que la longitud de un portaviones clase Nimitz", Cheer.phrase(portaviones, 333.0 * 1.2, fmt))
        assertEquals("el equivalente a 40 portaviones clase Nimitz", Cheer.phrase(portaviones, 333.0 * 40.4, fmt))
    }

    @Test fun genitivo() {
        assertEquals("de la altura del Everest", Cheer.genitive("la altura del Everest"))
        assertEquals("del Camino de Santiago", Cheer.genitive("el Camino de Santiago"))
        assertEquals("de una vuelta al mundo", Cheer.genitive("una vuelta al mundo"))
        assertEquals("de un maratón", Cheer.genitive("un maratón"))
    }

    @Test fun porcentaje_usa_genitivo_correcto() {
        val camino = ref("ruta:camino")
        assertEquals("el 50 % del Camino de Santiago (Camino Francés)", Cheer.phrase(camino, camino.meters * 0.5, fmt))
    }

    @Test fun distancias_invalidas_o_demasiado_pequenas_no_dan_mensaje() {
        for (d in listOf(0.0, -5.0, 49.9, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertNull("$d no debería dar mensaje", Cheer.compare(d, 1, fmt = fmt))
        }
    }

    @Test fun siempre_hay_mensaje_en_todo_el_rango_realista_y_el_texto_es_sano() {
        var d = 100.0
        while (d < 9.0e7) { // de 100 m a 90.000 km
            val c = Cheer.compare(d, 7, fmt = fmt)
            assertNotNull("sin mensaje para $d m", c)
            val t = c!!.text
            assertFalse("texto raro para $d m: $t", t.contains("NaN") || t.contains("Infinity") || t.contains("  ") || t.isBlank())
            d *= 1.07
        }
    }

    @Test fun es_determinista_por_semilla_y_varia_entre_semillas() {
        val a = Cheer.compare(102_700.0, 42, fmt = fmt)
        assertEquals(a, Cheer.compare(102_700.0, 42, fmt = fmt))
        val distintos = (0L..30L).mapNotNull { Cheer.compare(102_700.0, it, fmt = fmt)?.refId }.toSet()
        assertTrue("debería variar de referencia, salió: $distintos", distintos.size >= 3)
    }

    @Test fun semillas_negativas_tampoco_rompen() {
        assertNotNull(Cheer.compare(9_500.0, -123456789L, fmt = fmt))
        assertNotNull(Cheer.compare(9_500.0, Long.MIN_VALUE, fmt = fmt))
    }

    @Test fun respeta_las_referencias_a_evitar() {
        val first = Cheer.compare(9_500.0, 0, fmt = fmt)!!
        for (seed in 0L..20L) {
            assertTrue(Cheer.compare(9_500.0, seed, avoid = setOf(first.refId), fmt = fmt)!!.refId != first.refId)
        }
    }

    @Test fun lineas_tras_una_sesion_usan_referencias_distintas() {
        for (seed in 0L..50L) {
            val lines = Cheer.lines(9_500.0, 102_700.0, 487_500.0, seed, fmt)
            assertEquals(listOf(Cheer.Slot.SESSION, Cheer.Slot.TOTAL, Cheer.Slot.SHOES), lines.map { it.slot })
            val ids = lines.map { it.comparison!!.refId }
            assertEquals("referencias repetidas con semilla $seed: $ids", 3, ids.toSet().size)
        }
    }

    @Test fun sin_zapatilla_solo_hay_dos_lineas() {
        val lines = Cheer.lines(9_500.0, 102_700.0, null, 1, fmt)
        assertEquals(listOf(Cheer.Slot.SESSION, Cheer.Slot.TOTAL), lines.map { it.slot })
    }

    @Test fun una_sesion_minuscula_devuelve_la_linea_sin_comparacion() {
        val lines = Cheer.lines(30.0, 30.0, 30.0, 1, fmt)
        assertEquals(3, lines.size)
        assertTrue(lines.all { it.comparison == null })
    }

    @Test fun las_cifras_se_formatean_con_el_formateador_recibido() {
        val c = Cheer.compare(everest.meters * 2.4, 0, avoid = Cheer.refs.map { it.id }.toSet() - everest.id, fmt = fmt)!!
        assertEquals("2,4 veces la altura del Everest", c.text)
    }
}
