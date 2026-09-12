package com.eter.salud.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Aritmetica de instantes. Es la base de la retencion temporal de una franja: si
 * sumar minutos o comparar dos instantes fallara, una franja quedaria apartada
 * para siempre o se liberaria antes de tiempo, y en los dos casos dos pacientes
 * podrian acabar en la misma hora.
 */
class InstanteSaludTest {

    @Test
    fun sumar_minutos_avanza_dentro_de_la_misma_hora() {
        assertEquals(
            "2026-09-08T10:05:00Z",
            InstanteSalud.sumarMinutos("2026-09-08T10:00:00Z", 5),
        )
    }

    @Test
    fun sumar_minutos_arrastra_la_hora_al_pasar_de_sesenta() {
        assertEquals(
            "2026-09-08T11:10:00Z",
            InstanteSalud.sumarMinutos("2026-09-08T10:55:00Z", 15),
        )
    }

    @Test
    fun sumar_minutos_cruza_la_medianoche_y_cambia_el_dia() {
        assertEquals(
            "2026-09-09T00:03:00Z",
            InstanteSalud.sumarMinutos("2026-09-08T23:58:00Z", 5),
        )
    }

    @Test
    fun restar_minutos_cruza_la_medianoche_hacia_atras() {
        // El caso que rompe la division entera de Kotlin si no se usa `floorDiv`.
        assertEquals(
            "2026-09-07T23:55:00Z",
            InstanteSalud.sumarMinutos("2026-09-08T00:05:00Z", -10),
        )
    }

    @Test
    fun sumar_minutos_respeta_el_cambio_de_mes() {
        assertEquals(
            "2026-10-01T00:10:00Z",
            InstanteSalud.sumarMinutos("2026-09-30T23:50:00Z", 20),
        )
    }

    @Test
    fun sumar_minutos_conserva_los_segundos_del_instante_original() {
        assertEquals(
            "2026-09-08T10:05:42Z",
            InstanteSalud.sumarMinutos("2026-09-08T10:00:42Z", 5),
        )
    }

    @Test
    fun un_instante_mal_formado_no_produce_una_caducidad_inventada() {
        // Nulo y no el propio texto: una caducidad calculada sobre basura seria
        // una retencion que jamas expira.
        assertNull(InstanteSalud.sumarMinutos("2026-09-08 10:00", 5))
        assertNull(InstanteSalud.sumarMinutos("", 5))
    }

    @Test
    fun caduco_es_cierto_solo_cuando_el_instante_paso_del_limite() {
        val limite = "2026-09-08T10:05:00Z"
        assertTrue(InstanteSalud.caduco("2026-09-08T10:05:01Z", limite))
        assertFalse(InstanteSalud.caduco("2026-09-08T10:04:59Z", limite))
        // Justo en el limite todavia vale: el paciente que confirma en el ultimo
        // segundo no deberia perder su franja.
        assertFalse(InstanteSalud.caduco(limite, limite))
    }

    @Test
    fun un_instante_ilegible_nunca_se_da_por_caducado() {
        assertFalse(InstanteSalud.caduco("manana", "2026-09-08T10:05:00Z"))
        assertFalse(InstanteSalud.caduco("2026-09-08T10:05:00Z", "nunca"))
    }

    @Test
    fun la_fecha_civil_se_extrae_del_instante() {
        assertEquals("2026-09-08", InstanteSalud.fechaDe("2026-09-08T10:00:00Z"))
        assertEquals("", InstanteSalud.fechaDe("no es un instante"))
    }
}
