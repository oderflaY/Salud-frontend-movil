package com.eter.salud.data.repository

import com.eter.salud.domain.model.DiaDeAdherencia
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.ResumenAdherencia
import com.eter.salud.domain.model.TomaDelDia
import com.eter.salud.domain.repository.AdherenciaRepositorio
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema

/**
 * Implementacion temporal para desarrollo local: el tratamiento de cada
 * paciente y el estado de sus tomas de HOY, en memoria. Se sustituira por el
 * cliente HTTP contra el backend en Go sin tocar los ViewModels.
 *
 * ## Cada paciente, sus medicinas
 *
 * [tomasPorPaciente] trae el tratamiento de cada paciente de demostracion; un
 * paciente que no esta en el mapa recibe [tomasIniciales]. Antes todos los
 * pacientes compartian las mismas dos pastillas, y marcar una en una cuenta la
 * marcaba en todas.
 *
 * ## Los dias que no son hoy
 *
 * Solo hoy se registra de verdad. Los dias pasados se reconstruyen con un
 * patron FIJO de olvidos (dos dias de la semana con una toma omitida), para que
 * el calendario y la lista de cada dia digan lo mismo: si la casilla del martes
 * dice "falto alguna", al abrir el martes aparece cual. Los dias futuros salen
 * todas pendientes. Fijo y no al azar: una semana que cambia de colores en cada
 * recomposicion haria imposible juzgar el diseno.
 */
class AdherenciaRepositorioEnMemoria(
    private val tomasIniciales: List<TomaDelDia> = TOMAS_DEMO,
    tomasPorPaciente: Map<String, List<TomaDelDia>> = emptyMap(),
    private val reloj: RelojSalud = relojDelSistema(),
) : AdherenciaRepositorio {

    /** Estado de las tomas de HOY, por paciente. */
    private val tomasDeHoy: MutableMap<String, List<TomaDelDia>> = tomasPorPaciente.toMutableMap()

    private fun tratamientoDe(idPaciente: String): List<TomaDelDia> =
        tomasDeHoy.getOrPut(idPaciente) { tomasIniciales }

    override suspend fun obtenerTomasDelDia(
        idPaciente: String,
        fecha: String,
    ): Result<List<TomaDelDia>> = Result.success(tomasDelDia(idPaciente, fecha))

    override suspend fun registrarToma(
        idPaciente: String,
        idToma: String,
        estado: EstadoToma,
        instante: String,
    ): Result<Unit> {
        tomasDeHoy[idPaciente] = tratamientoDe(idPaciente).map {
            if (it.idToma == idToma) it.copy(estado = estado) else it
        }
        return Result.success(Unit)
    }

    override suspend fun obtenerSemana(
        idPaciente: String,
        fechaFinal: String,
    ): Result<List<DiaDeAdherencia>> {
        val hoy = reloj.fechaHoy()
        val dias = (DIAS_SEMANA - 1 downTo 0).map { atras ->
            val fecha = CalendarioSalud.restarDias(fechaFinal, atras)
            val tomas = tomasDelDia(idPaciente, fecha)
            DiaDeAdherencia(
                fecha = fecha,
                tomasProgramadas = tomas.size,
                tomasCumplidas = tomas.count { it.estado.cumplida },
                // Hoy no se juzga hasta que acaba, y lo futuro tampoco.
                enCurso = fecha >= hoy,
            )
        }
        return Result.success(dias)
    }

    override suspend fun obtenerResumenSemanal(
        idPaciente: String,
        fechaFinal: String,
    ): Result<ResumenAdherencia> {
        val dias = (DIAS_SEMANA - 1 downTo 0).map { CalendarioSalud.restarDias(fechaFinal, it) }
        val tomas = dias.flatMap { tomasDelDia(idPaciente, it) }
        return Result.success(
            ResumenAdherencia(
                tomasProgramadas = tomas.size,
                tomasCumplidas = tomas.count { it.estado.cumplida },
            ),
        )
    }

    private fun tomasDelDia(idPaciente: String, fecha: String): List<TomaDelDia> {
        val hoy = reloj.fechaHoy()
        val tratamiento = tratamientoDe(idPaciente)
        return when {
            fecha == hoy -> tratamiento
            fecha > hoy -> tratamiento.map { it.copy(idToma = "${it.idToma}_$fecha", estado = EstadoToma.PENDIENTE) }
            else -> {
                val atras = CalendarioSalud.diasEntre(fecha, hoy) ?: 0
                val olvidadas = PATRON_OLVIDOS[atras % PATRON_OLVIDOS.size]
                tratamiento.mapIndexed { indice, toma ->
                    // El olvido cae en la ULTIMA toma del dia: es la de la noche,
                    // la que de verdad se olvida con mas frecuencia.
                    val omitida = indice >= tratamiento.size - olvidadas
                    toma.copy(
                        idToma = "${toma.idToma}_$fecha",
                        estado = if (omitida) EstadoToma.OMITIDO else EstadoToma.TOMADO,
                    )
                }
            }
        }
    }

    private val EstadoToma.cumplida: Boolean
        get() = this == EstadoToma.TOMADO || this == EstadoToma.TOMADO_TARDE

    private companion object {
        const val DIAS_SEMANA = 7

        /** Tomas olvidadas segun cuantos dias atras: dos dias con un olvido. */
        val PATRON_OLVIDOS = listOf(0, 0, 1, 0, 0, 1, 0)

        val TOMAS_DEMO = listOf(
            TomaDelDia(
                idToma = "log_8899",
                idTratamiento = "trt_001",
                medicamento = "Losartan",
                dosis = "50 mg, 1 tableta",
                horaProgramada = "08:00",
                estado = EstadoToma.PENDIENTE,
            ),
            TomaDelDia(
                idToma = "log_8900",
                idTratamiento = "trt_001",
                medicamento = "Losartan",
                dosis = "50 mg, 1 tableta",
                horaProgramada = "20:00",
                estado = EstadoToma.PENDIENTE,
            ),
        )
    }
}
