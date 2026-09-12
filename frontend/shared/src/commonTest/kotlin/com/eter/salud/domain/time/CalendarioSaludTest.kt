package com.eter.salud.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Aritmetica de fechas del calendario civil. Vive en `commonMain` y no en cada
 * plataforma para que el selector de fechas se comporte igual en Android y iOS
 * y para poder probarlo sin depender del reloj del dispositivo.
 */
class CalendarioSaludTest {

    @Test
    fun restar_dias_dentro_del_mismo_mes() {
        assertEquals("2026-09-05", CalendarioSalud.restarDias("2026-09-12", 7))
    }

    @Test
    fun restar_dias_cruzando_el_cambio_de_mes() {
        assertEquals("2026-08-31", CalendarioSalud.restarDias("2026-09-01", 1))
    }

    @Test
    fun restar_dias_cruzando_el_cambio_de_anio() {
        assertEquals("2025-12-31", CalendarioSalud.restarDias("2026-01-01", 1))
    }

    @Test
    fun restar_un_mes_respeta_la_longitud_del_mes_anterior() {
        assertEquals("2026-02-28", CalendarioSalud.restarDias("2026-03-31", 31))
    }

    @Test
    fun el_anio_bisiesto_conserva_el_veintinueve_de_febrero() {
        assertEquals("2024-02-29", CalendarioSalud.restarDias("2024-03-01", 1))
    }

    @Test
    fun un_anio_secular_no_divisible_entre_cuatrocientos_no_es_bisiesto() {
        assertFalse(CalendarioSalud.esFechaValida("1900-02-29"))
        assertTrue(CalendarioSalud.esFechaValida("2000-02-29"))
    }

    @Test
    fun una_fecha_mal_formada_no_es_valida() {
        assertFalse(CalendarioSalud.esFechaValida("12-04-1985"))
        assertFalse(CalendarioSalud.esFechaValida("2026-13-01"))
        assertFalse(CalendarioSalud.esFechaValida("2026-04-31"))
        assertFalse(CalendarioSalud.esFechaValida(""))
    }

    @Test
    fun componer_arma_la_fecha_solo_cuando_las_tres_partes_existen_en_el_calendario() {
        assertEquals("1985-04-12", CalendarioSalud.componer(dia = "12", mes = "4", anio = "1985"))
        assertEquals("1985-04-12", CalendarioSalud.componer(dia = "12", mes = "04", anio = "1985"))
        assertNull(CalendarioSalud.componer(dia = "31", mes = "02", anio = "1985"))
        assertNull(CalendarioSalud.componer(dia = "", mes = "04", anio = "1985"))
    }

    @Test
    fun la_edad_descuenta_el_anio_cuando_aun_no_llega_el_cumpleanios() {
        assertEquals(28, CalendarioSalud.edadEnAnios("1998-05-15", "2026-09-06"))
        assertEquals(27, CalendarioSalud.edadEnAnios("1998-05-15", "2026-05-14"))
        assertEquals(28, CalendarioSalud.edadEnAnios("1998-05-15", "2026-05-15"))
    }

    @Test
    fun una_edad_imposible_no_se_muestra_en_lugar_de_inventarse() {
        assertNull(CalendarioSalud.edadEnAnios("2027-01-01", "2026-09-06"))
        assertNull(CalendarioSalud.edadEnAnios("no es una fecha", "2026-09-06"))
    }

    @Test
    fun descomponer_devuelve_las_partes_para_rellenar_los_campos() {
        val partes = CalendarioSalud.descomponer("1985-04-12")

        assertEquals("12", partes?.dia)
        assertEquals("04", partes?.mes)
        assertEquals("1985", partes?.anio)
        assertNull(CalendarioSalud.descomponer("no es una fecha"))
    }
}

/**
 * Aritmetica que necesita el calendario del medico: recorrer un rango dia a dia
 * y encuadrar semanas y meses. Un error aqui pintaria las citas bajo la columna
 * equivocada o se saltaria un dia entero de la agenda.
 */
class CalendarioSaludAgendaTest {

    @Test
    fun sumar_dias_cruza_el_fin_de_mes() {
        assertEquals("2026-10-01", CalendarioSalud.sumarDias("2026-09-30", 1))
        assertEquals("2027-01-01", CalendarioSalud.sumarDias("2026-12-31", 1))
    }

    @Test
    fun sumar_dias_respeta_el_anio_bisiesto() {
        assertEquals("2028-02-29", CalendarioSalud.sumarDias("2028-02-28", 1))
        assertEquals("2027-03-01", CalendarioSalud.sumarDias("2027-02-28", 1))
    }

    @Test
    fun dias_entre_cuenta_hacia_adelante_y_hacia_atras() {
        assertEquals(7, CalendarioSalud.diasEntre("2026-09-01", "2026-09-08"))
        assertEquals(-7, CalendarioSalud.diasEntre("2026-09-08", "2026-09-01"))
        assertEquals(0, CalendarioSalud.diasEntre("2026-09-08", "2026-09-08"))
    }

    @Test
    fun los_extremos_del_mes_respetan_los_meses_cortos_y_los_bisiestos() {
        assertEquals("2026-02-01", CalendarioSalud.primerDiaDelMes("2026-02-17"))
        assertEquals("2026-02-28", CalendarioSalud.ultimoDiaDelMes("2026-02-17"))
        assertEquals("2028-02-29", CalendarioSalud.ultimoDiaDelMes("2028-02-17"))
        assertEquals("2026-09-30", CalendarioSalud.ultimoDiaDelMes("2026-09-08"))
    }

    @Test
    fun el_dia_de_la_semana_cuenta_el_lunes_como_cero() {
        // 2026-09-07 fue lunes.
        assertEquals(0, CalendarioSalud.diaDeLaSemana("2026-09-07"))
        assertEquals(1, CalendarioSalud.diaDeLaSemana("2026-09-08"))
        assertEquals(6, CalendarioSalud.diaDeLaSemana("2026-09-13"))
    }

    @Test
    fun una_fecha_invalida_no_devuelve_un_dia_de_la_semana_inventado() {
        assertNull(CalendarioSalud.diaDeLaSemana("2026-02-30"))
        assertNull(CalendarioSalud.primerDiaDelMes("no es fecha"))
        assertNull(CalendarioSalud.diasEntre("2026-13-01", "2026-09-08"))
    }
}
