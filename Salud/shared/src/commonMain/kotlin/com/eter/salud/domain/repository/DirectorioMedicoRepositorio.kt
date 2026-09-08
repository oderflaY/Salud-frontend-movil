package com.eter.salud.domain.repository

import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.PerfilDoctorDirectorio

/**
 * Contrato de la seccion "Mi Medico" hacia la API en Go: saber si el paciente
 * ya tiene un doctor vinculado, buscar en el Directorio Medico, y solicitar una
 * vinculacion nueva.
 */
interface DirectorioMedicoRepositorio {

    /** `null` si el paciente aun no tiene ningun doctor vinculado. */
    suspend fun obtenerMedicoVinculado(idPaciente: String): Result<MedicoVinculado?>

    /**
     * Doctores verificados del directorio. [especialidad] nulo trae todas.
     * La busqueda por texto se filtra en el cliente sobre esta lista, para no
     * golpear la red en cada tecla escrita en la barra de busqueda.
     */
    suspend fun buscarDirectorio(especialidad: Especialidad?): Result<List<PerfilDoctorDirectorio>>

    /** Vincula al paciente con el doctor elegido y abre el canal de chat. */
    suspend fun solicitarVinculacion(idPaciente: String, idMedico: String): Result<MedicoVinculado>
}
