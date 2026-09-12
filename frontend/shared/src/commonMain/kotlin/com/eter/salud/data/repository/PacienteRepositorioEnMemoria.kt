package com.eter.salud.data.repository

import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.PacienteJson
import com.eter.salud.domain.repository.PacienteRepositorio

/**
 * Implementacion temporal para desarrollo local: conserva el ultimo payload
 * serializado en memoria en lugar de llamar a la API.
 *
 * Se sustituira por el cliente HTTP contra el backend en Go sin tocar el
 * ViewModel, que solo depende de [PacienteRepositorio].
 */
class PacienteRepositorioEnMemoria : PacienteRepositorio {

    var ultimoPayload: String? = null
        private set

    override suspend fun registrarPaciente(paciente: PacienteDto): Result<String> {
        ultimoPayload = PacienteJson.encodeToString(PacienteDto.serializer(), paciente)
        return Result.success(ID_PACIENTE_LOCAL)
    }

    private companion object {
        const val ID_PACIENTE_LOCAL = "pac_local_dev"
    }
}
