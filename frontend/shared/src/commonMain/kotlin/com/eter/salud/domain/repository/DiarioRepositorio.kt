package com.eter.salud.domain.repository

import com.eter.salud.domain.model.EntradaDiario
import kotlinx.coroutines.flow.Flow

/**
 * Bitacora de sintomas del paciente.
 *
 * Es local por diseno, no por falta de backend: el paciente escribe sintomas
 * aunque no tenga cobertura, y el semaforo tiene que estar listo en ese momento.
 * Lo que viaja al medico es el resultado, no el borrador.
 */
interface DiarioRepositorio {

    /** Entradas del paciente, mas reciente primero, en vivo. */
    fun entradasDe(idPaciente: String): Flow<List<EntradaDiario>>

    suspend fun guardar(entrada: EntradaDiario): Result<Unit>

    suspend fun eliminar(idEntrada: String): Result<Unit>

    /**
     * Ultima entrada del paciente. La usa la bandeja del medico para pintar el
     * punto de prioridad sin descargar la bitacora entera de cada paciente.
     */
    suspend fun ultimaEntradaDe(idPaciente: String): Result<EntradaDiario?>
}
