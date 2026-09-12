package com.eter.salud.presentation.perfil

import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.repository.HistorialMedicoRepositorio

/**
 * Repositorio falso de la Fase 2. Registra lo enviado y permite simular la
 * caida de red para probar el camino de error sin tocar la implementacion real.
 */
class HistorialMedicoRepositorioFalso(
    private val perfilRemoto: Result<PacienteDto> = Result.success(PacienteDto()),
    private val resultadoGuardado: Result<Unit> = Result.success(Unit),
) : HistorialMedicoRepositorio {

    var lecturas: Int = 0
        private set

    var guardados: Int = 0
        private set

    var ultimoDtoGuardado: PacienteDto? = null
        private set

    override suspend fun obtenerPaciente(idPaciente: String): Result<PacienteDto> {
        lecturas++
        return perfilRemoto
    }

    override suspend fun actualizarHistorial(paciente: PacienteDto): Result<Unit> {
        guardados++
        ultimoDtoGuardado = paciente
        return resultadoGuardado
    }
}
