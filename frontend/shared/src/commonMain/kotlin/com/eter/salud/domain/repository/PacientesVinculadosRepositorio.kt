package com.eter.salud.domain.repository

import com.eter.salud.domain.model.PacienteVinculado

/**
 * Cartera de pacientes de un profesional (DM_PerfilMedico.md, seccion 3:
 * `pacientesVinculados`). Separado del historial del paciente porque aqui solo
 * se pide el resumen ligero para el semaforo de riesgo de la Home, nunca el
 * expediente completo.
 */
interface PacientesVinculadosRepositorio {

    suspend fun obtenerPacientesVinculados(idMedico: String): Result<List<PacienteVinculado>>
}
