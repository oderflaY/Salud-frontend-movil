package com.eter.salud.data.repository

import com.eter.salud.domain.model.EntradaDiario
import com.eter.salud.domain.repository.DiarioRepositorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Bitacora simulada. Se sustituira por la tabla local de SQLite sin tocar el
 * ViewModel, que solo depende de [DiarioRepositorio].
 */
class DiarioRepositorioEnMemoria : DiarioRepositorio {

    private val entradas = MutableStateFlow<List<EntradaDiario>>(emptyList())

    override fun entradasDe(idPaciente: String): Flow<List<EntradaDiario>> =
        entradas.asStateFlow().map { todas ->
            todas.filter { it.idPaciente == idPaciente }.sortedByDescending { it.instante }
        }

    override suspend fun guardar(entrada: EntradaDiario): Result<Unit> {
        entradas.value = entradas.value.filterNot { it.idEntrada == entrada.idEntrada } + entrada
        return Result.success(Unit)
    }

    override suspend fun eliminar(idEntrada: String): Result<Unit> {
        entradas.value = entradas.value.filterNot { it.idEntrada == idEntrada }
        return Result.success(Unit)
    }

    override suspend fun ultimaEntradaDe(idPaciente: String): Result<EntradaDiario?> =
        Result.success(
            entradas.value.filter { it.idPaciente == idPaciente }.maxByOrNull { it.instante },
        )
}
