package com.eter.salud.domain.repository

import com.eter.salud.domain.model.PacienteDto

/**
 * Contrato de la Fase 2 hacia la API en Go.
 *
 * Se mantiene aparte de [PacienteRepositorio] a proposito: el alta del paciente
 * y la edicion de su historial son operaciones con permisos distintos
 * (DM_HistorialMedico.md, seccion 2: Segmentacion de Seguridad). Quien solo
 * necesita registrar no debe poder leer el expediente completo.
 */
interface HistorialMedicoRepositorio {

    /**
     * Descarga el expediente para hidratar la pantalla de ajustes.
     * Exige un token con privilegios sobre el propio paciente.
     */
    suspend fun obtenerPaciente(idPaciente: String): Result<PacienteDto>

    /** Persiste el expediente editado. El backend asigna y conserva los IDs. */
    suspend fun actualizarHistorial(paciente: PacienteDto): Result<Unit>
}
