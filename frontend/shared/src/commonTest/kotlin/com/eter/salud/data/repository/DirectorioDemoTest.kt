package com.eter.salud.data.repository

import com.eter.salud.domain.model.Especialidad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** El directorio ofrece opciones reales para escoger en cada especialidad. */
class DirectorioDemoTest {

    private val doctores = DirectorioMedicoRepositorioMock.DOCTORES_DEMO

    @Test
    fun cada_especialidad_tiene_al_menos_tres_medicos_para_escoger() {
        Especialidad.entries.forEach { especialidad ->
            val cuantos = doctores.count { it.especialidad == especialidad }
            assertTrue(cuantos >= 3, "$especialidad solo tiene $cuantos")
        }
    }

    @Test
    fun ningun_medico_repite_identificador() {
        assertEquals(doctores.size, doctores.map { it.idMedico }.toSet().size)
    }
}
