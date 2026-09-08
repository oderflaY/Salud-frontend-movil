package com.eter.salud.data.repository

import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.PacienteJson
import com.eter.salud.domain.repository.HistorialMedicoRepositorio

/**
 * Implementacion temporal de la Fase 2 para desarrollo local: guarda el
 * expediente en memoria en lugar de llamar a la API.
 *
 * Se sustituira por el cliente HTTP contra el backend en Go sin tocar el
 * ViewModel, que solo depende de [HistorialMedicoRepositorio].
 */
class HistorialMedicoRepositorioEnMemoria(
    perfilInicial: PacienteDto = PacienteDto(),
) : HistorialMedicoRepositorio {

    private var perfil: PacienteDto = perfilInicial

    var ultimoPayload: String? = null
        private set

    override suspend fun obtenerPaciente(idPaciente: String): Result<PacienteDto> =
        Result.success(perfil.copy(idPaciente = idPaciente))

    override suspend fun actualizarHistorial(paciente: PacienteDto): Result<Unit> {
        perfil = paciente
        ultimoPayload = PacienteJson.encodeToString(PacienteDto.serializer(), paciente)
        return Result.success(Unit)
    }
}
