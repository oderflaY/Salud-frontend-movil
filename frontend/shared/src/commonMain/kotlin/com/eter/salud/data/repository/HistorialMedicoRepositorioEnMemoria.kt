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
    /**
     * Expediente de cada paciente de demostracion. Un paciente que no esta aqui
     * recibe [perfilInicial]; antes TODOS lo recibian, y por eso el panel de
     * cualquier cuenta arrancaba sin nombre.
     */
    perfiles: Map<String, PacienteDto> = emptyMap(),
) : HistorialMedicoRepositorio {

    private var perfil: PacienteDto = perfilInicial
    private val perfilesPorPaciente = perfiles.toMutableMap()

    var ultimoPayload: String? = null
        private set

    override suspend fun obtenerPaciente(idPaciente: String): Result<PacienteDto> =
        Result.success((perfilesPorPaciente[idPaciente] ?: perfil).copy(idPaciente = idPaciente))

    override suspend fun actualizarHistorial(paciente: PacienteDto): Result<Unit> {
        val id = paciente.idPaciente
        if (id != null && perfilesPorPaciente.containsKey(id)) perfilesPorPaciente[id] = paciente
        perfil = paciente
        ultimoPayload = PacienteJson.encodeToString(PacienteDto.serializer(), paciente)
        return Result.success(Unit)
    }
}
