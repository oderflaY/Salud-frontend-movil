package com.eter.salud.data.repository

import com.eter.salud.data.db.BaseSalud
import com.eter.salud.data.local.ContextoDeBase
import com.eter.salud.domain.model.DiaDeAdherencia
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.ResumenAdherencia
import com.eter.salud.domain.model.TomaDelDia
import com.eter.salud.domain.repository.AdherenciaRepositorio
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.tratamiento.HorarioDeTratamiento
import com.eter.salud.domain.tratamiento.IdDeToma
import kotlinx.coroutines.withContext

/**
 * Tomas de medicamentos sobre la base SQLite del dispositivo.
 *
 * ## De donde sale cada toma
 *
 * Lo PROGRAMADO se deriva del tratamiento que esta en el expediente (con
 * [HorarioDeTratamiento]); lo REGISTRADO sale de la tabla `registro_toma`. Asi
 * cada paciente tiene sus medicinas -- las que escribio al crear su expediente
 * -- y no las dos pastillas de ejemplo que antes recibia cualquier cuenta.
 *
 * ## Los dias que no son hoy
 *
 * Una toma de un dia ya pasado sin registro fue una toma que no se marco, y se
 * cuenta como omitida. Un dia futuro sale entero pendiente. Y los dias previos
 * a `tomas_desde` (antes de existir el expediente) no tienen tomas: no se puede
 * culpar a nadie de olvidar una medicina que todavia no le habian recetado.
 */
class AdherenciaRepositorioLocal(
    private val base: BaseSalud,
    private val reloj: RelojSalud,
) : AdherenciaRepositorio {

    override suspend fun obtenerTomasDelDia(
        idPaciente: String,
        fecha: String,
    ): Result<List<TomaDelDia>> = withContext(ContextoDeBase) {
        Result.success(tomasEntre(idPaciente, fecha, fecha)[fecha].orEmpty())
    }

    override suspend fun registrarToma(
        idPaciente: String,
        idToma: String,
        estado: EstadoToma,
        instante: String,
    ): Result<Unit> = withContext(ContextoDeBase) {
        val (clave, fecha) = IdDeToma.descomponer(idToma)
            ?: return@withContext Result.failure(IllegalArgumentException("Toma desconocida: $idToma"))
        base.adherenciaQueries.registrar(idPaciente, fecha, clave, estado.valorApi, instante)
        Result.success(Unit)
    }

    override suspend fun obtenerSemana(
        idPaciente: String,
        fechaFinal: String,
    ): Result<List<DiaDeAdherencia>> = withContext(ContextoDeBase) {
        val hoy = reloj.fechaHoy()
        val lunesDeLaVentana = CalendarioSalud.restarDias(fechaFinal, DIAS_SEMANA - 1)
        val porDia = tomasEntre(idPaciente, lunesDeLaVentana, fechaFinal)
        val dias = (DIAS_SEMANA - 1 downTo 0).map { atras ->
            val fecha = CalendarioSalud.restarDias(fechaFinal, atras)
            val tomas = porDia[fecha].orEmpty()
            DiaDeAdherencia(
                fecha = fecha,
                tomasProgramadas = tomas.size,
                tomasCumplidas = tomas.count { it.estado.cumplida },
                // Hoy no se juzga hasta que acaba, y lo futuro tampoco.
                enCurso = fecha >= hoy,
            )
        }
        Result.success(dias)
    }

    override suspend fun obtenerResumenSemanal(
        idPaciente: String,
        fechaFinal: String,
    ): Result<ResumenAdherencia> = withContext(ContextoDeBase) {
        val desde = CalendarioSalud.restarDias(fechaFinal, DIAS_SEMANA - 1)
        val tomas = tomasEntre(idPaciente, desde, fechaFinal).values.flatten()
        Result.success(
            ResumenAdherencia(
                tomasProgramadas = tomas.size,
                tomasCumplidas = tomas.count { it.estado.cumplida },
            ),
        )
    }

    /** Tomas de cada dia del rango, ambos extremos incluidos. */
    private fun tomasEntre(idPaciente: String, desde: String, hasta: String): Map<String, List<TomaDelDia>> {
        val fechas = fechasEntre(desde, hasta)
        val fila = base.expedienteQueries.obtener(idPaciente).executeAsOneOrNull()
            ?: return fechas.associateWith { emptyList() }
        val tratamiento = leerExpediente(base, idPaciente)?.tratamientosActivos.orEmpty()
        val programadas = HorarioDeTratamiento.tomasProgramadas(tratamiento)
        val registros = base.adherenciaQueries.registrosEntre(idPaciente, desde, hasta)
            .executeAsList()
            .associate { (it.fecha to it.clave_toma) to EstadoToma.desdeApi(it.estado) }
        val hoy = reloj.fechaHoy()

        return fechas.associateWith { fecha ->
            if (fecha < fila.tomas_desde) return@associateWith emptyList()
            programadas.map { programada ->
                val registrado = registros[fecha to programada.clave]
                val estado = when {
                    registrado != null && fecha <= hoy -> registrado
                    fecha < hoy -> EstadoToma.OMITIDO
                    else -> EstadoToma.PENDIENTE
                }
                programada.enFecha(fecha, estado)
            }
        }
    }

    private fun fechasEntre(desde: String, hasta: String): List<String> {
        val dias = CalendarioSalud.diasEntre(desde, hasta)?.takeIf { it >= 0 } ?: return listOf(desde)
        return (0..dias).map { CalendarioSalud.sumarDias(desde, it) }
    }

    private val EstadoToma.cumplida: Boolean
        get() = this == EstadoToma.TOMADO || this == EstadoToma.TOMADO_TARDE

    private companion object {
        const val DIAS_SEMANA = 7
    }
}
