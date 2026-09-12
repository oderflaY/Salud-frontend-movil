package com.eter.salud.domain.tratamiento

import com.eter.salud.domain.model.TratamientoActivo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Del tratamiento a las horas de toma.
 *
 * Es la regla que decide que pastillas ve el paciente en su panel cada dia: un
 * error aqui no se ve como un fallo, se ve como una medicina que no aparece.
 */
class HorarioDeTratamientoTest {

    private fun tratamiento(frecuencia: Int, horarios: List<String> = emptyList(), id: String? = null) =
        TratamientoActivo(
            idTratamiento = id,
            medicamento = "Metformina",
            dosis = "850 mg",
            frecuenciaHoras = frecuencia,
            horariosSugeridos = horarios,
        )

    @Test
    fun cada_ocho_horas_empieza_a_las_ocho_y_reparte_el_dia() {
        assertEquals(listOf("00:00", "08:00", "16:00"), HorarioDeTratamiento.horariosDe(tratamiento(8)))
        assertEquals(listOf("08:00", "20:00"), HorarioDeTratamiento.horariosDe(tratamiento(12)))
        assertEquals(listOf("08:00"), HorarioDeTratamiento.horariosDe(tratamiento(24)))
    }

    @Test
    fun los_horarios_sugeridos_mandan_sobre_la_frecuencia() {
        val horas = HorarioDeTratamiento.horariosDe(tratamiento(8, horarios = listOf("21:00", "09:30", "9:00")))
        // "9:00" no es HH:MM: se descarta en vez de adivinar que quiso decir.
        assertEquals(listOf("09:30", "21:00"), horas)
    }

    @Test
    fun una_frecuencia_invalida_deja_una_toma_al_dia_y_nunca_ninguna() {
        assertEquals(listOf("08:00"), HorarioDeTratamiento.horariosDe(tratamiento(0)))
        assertEquals(listOf("08:00"), HorarioDeTratamiento.horariosDe(tratamiento(72)))
    }

    @Test
    fun las_tomas_del_dia_salen_ordenadas_por_hora_con_clave_estable() {
        val tomas = HorarioDeTratamiento.tomasProgramadas(
            listOf(
                tratamiento(12, id = "trt_metformina"),
                TratamientoActivo(idTratamiento = "trt_losartan", medicamento = "Losartan", dosis = "50 mg", frecuenciaHoras = 24),
            ),
        )

        assertEquals(listOf("08:00", "08:00", "20:00"), tomas.map { it.hora })
        assertEquals("trt_losartan_0800", tomas.first().clave)
        assertEquals("trt_metformina_2000", tomas.last().clave)
    }

    @Test
    fun el_id_de_una_toma_lleva_la_fecha_y_se_puede_desarmar() {
        val id = IdDeToma.componer("trt_metformina_0800", "2026-09-10")

        assertEquals("trt_metformina_0800" to "2026-09-10", IdDeToma.descomponer(id))
        assertNull(IdDeToma.descomponer("toma_sin_fecha"))
    }
}
