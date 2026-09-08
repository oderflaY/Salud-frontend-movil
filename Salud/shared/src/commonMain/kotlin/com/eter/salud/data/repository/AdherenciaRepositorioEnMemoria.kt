package com.eter.salud.data.repository

import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.ResumenAdherencia
import com.eter.salud.domain.model.TomaDelDia
import com.eter.salud.domain.repository.AdherenciaRepositorio

/**
 * Implementacion temporal para desarrollo local: genera un dia de tomas y
 * mantiene su estado en memoria. Se sustituira por el cliente HTTP contra el
 * backend en Go sin tocar el ViewModel.
 *
 * El resumen semanal se calcula sobre un historico ficticio mas las tomas de
 * hoy, para que la barra de adherencia se mueva al registrar una toma.
 */
class AdherenciaRepositorioEnMemoria(
    tomasIniciales: List<TomaDelDia> = TOMAS_DEMO,
    private val historicoProgramadas: Int = PROGRAMADAS_SEMANA_DEMO,
    private val historicoCumplidas: Int = CUMPLIDAS_PREVIAS_DEMO,
) : AdherenciaRepositorio {

    private var tomas: List<TomaDelDia> = tomasIniciales

    override suspend fun obtenerTomasDelDia(
        idPaciente: String,
        fecha: String,
    ): Result<List<TomaDelDia>> = Result.success(tomas)

    override suspend fun registrarToma(
        idPaciente: String,
        idToma: String,
        estado: EstadoToma,
        instante: String,
    ): Result<Unit> {
        tomas = tomas.map { if (it.idToma == idToma) it.copy(estado = estado) else it }
        return Result.success(Unit)
    }

    override suspend fun obtenerResumenSemanal(
        idPaciente: String,
        fechaFinal: String,
    ): Result<ResumenAdherencia> {
        val cumplidasHoy = tomas.count {
            it.estado == EstadoToma.TOMADO || it.estado == EstadoToma.TOMADO_TARDE
        }
        return Result.success(
            ResumenAdherencia(
                tomasProgramadas = historicoProgramadas,
                tomasCumplidas = historicoCumplidas + cumplidasHoy,
            ),
        )
    }

    private companion object {
        const val PROGRAMADAS_SEMANA_DEMO = 14
        const val CUMPLIDAS_PREVIAS_DEMO = 9

        val TOMAS_DEMO = listOf(
            TomaDelDia(
                idToma = "log_8899",
                idTratamiento = "trt_001",
                medicamento = "Losartan",
                dosis = "50mg",
                horaProgramada = "08:00",
                estado = EstadoToma.PENDIENTE,
            ),
            TomaDelDia(
                idToma = "log_8900",
                idTratamiento = "trt_001",
                medicamento = "Losartan",
                dosis = "50mg",
                horaProgramada = "20:00",
                estado = EstadoToma.PENDIENTE,
            ),
        )
    }
}
