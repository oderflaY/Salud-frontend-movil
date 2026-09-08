package com.eter.salud.domain.repository

import com.eter.salud.domain.model.PacienteDto

/**
 * Contrato de la capa Model hacia la API en Go.
 * El ViewModel solo conoce esta interfaz, nunca la implementacion de red.
 */
interface PacienteRepositorio {

    /**
     * Registra el perfil del paciente.
     * @return el `idPaciente` asignado por el backend (prefijo `pac_`).
     */
    suspend fun registrarPaciente(paciente: PacienteDto): Result<String>
}
