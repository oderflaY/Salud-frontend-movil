package com.eter.salud.presentation.home

import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.ResumenAdherencia
import com.eter.salud.domain.model.TomaDelDia
import com.eter.salud.domain.repository.AdherenciaRepositorio

/**
 * Repositorio de adherencia falso. El resumen semanal se puede encolar para
 * comprobar que la pantalla lo refresca despues de registrar una toma.
 */
class AdherenciaRepositorioFalso(
    private val tomas: Result<List<TomaDelDia>> = Result.success(emptyList()),
    private val resumenes: List<Result<ResumenAdherencia>> =
        listOf(Result.success(ResumenAdherencia(tomasProgramadas = 0, tomasCumplidas = 0))),
    private val resultadoRegistro: Result<Unit> = Result.success(Unit),
) : AdherenciaRepositorio {

    private var lecturasResumen = 0

    var registros: MutableList<Pair<String, EstadoToma>> = mutableListOf()
        private set

    override suspend fun obtenerTomasDelDia(
        idPaciente: String,
        fecha: String,
    ): Result<List<TomaDelDia>> = tomas

    override suspend fun registrarToma(
        idPaciente: String,
        idToma: String,
        estado: EstadoToma,
        instante: String,
    ): Result<Unit> {
        registros += idToma to estado
        return resultadoRegistro
    }

    override suspend fun obtenerResumenSemanal(
        idPaciente: String,
        fechaFinal: String,
    ): Result<ResumenAdherencia> {
        val resumen = resumenes.getOrElse(lecturasResumen) { resumenes.last() }
        lecturasResumen++
        return resumen
    }
}

/** Historial falso reducido a lo que la pantalla de inicio necesita leer. */
class HistorialParaInicioFalso(
    private val perfil: Result<PacienteDto> = Result.success(PacienteDto()),
) : com.eter.salud.domain.repository.HistorialMedicoRepositorio {

    override suspend fun obtenerPaciente(idPaciente: String): Result<PacienteDto> = perfil

    override suspend fun actualizarHistorial(paciente: PacienteDto): Result<Unit> =
        Result.success(Unit)
}
