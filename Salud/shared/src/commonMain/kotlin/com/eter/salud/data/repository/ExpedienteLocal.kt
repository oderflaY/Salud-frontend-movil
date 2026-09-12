package com.eter.salud.data.repository

import com.eter.salud.data.db.BaseSalud
import com.eter.salud.data.local.ContextoDeBase
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.PacienteJson
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.domain.repository.HistorialMedicoRepositorio
import com.eter.salud.domain.repository.PacienteRepositorio
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.tratamiento.HorarioDeTratamiento
import kotlinx.coroutines.withContext

/**
 * Expediente medico en la base SQLite del dispositivo.
 *
 * Es lo que cumple "salir de la cuenta sin perder el perfil": el expediente vive
 * aqui y no en la sesion, asi que cerrar sesion y volver a entrar lo encuentra
 * intacto. Se sustituira por el cliente HTTP contra el backend en Go sin tocar
 * los ViewModels, que solo dependen de [HistorialMedicoRepositorio].
 */
class HistorialMedicoRepositorioLocal(
    private val base: BaseSalud,
    private val reloj: RelojSalud,
) : HistorialMedicoRepositorio {

    /** Un paciente sin expediente todavia recibe uno vacio, no un error. */
    override suspend fun obtenerPaciente(idPaciente: String): Result<PacienteDto> =
        withContext(ContextoDeBase) {
            Result.success(leerExpediente(base, idPaciente) ?: PacienteDto(idPaciente = idPaciente))
        }

    override suspend fun actualizarHistorial(paciente: PacienteDto): Result<Unit> =
        withContext(ContextoDeBase) {
            val idPaciente = paciente.idPaciente
                ?: return@withContext Result.failure(IllegalArgumentException("Expediente sin idPaciente"))
            guardarExpediente(base, reloj, paciente.copy(idPaciente = idPaciente))
            Result.success(Unit)
        }
}

/**
 * Cierre del expediente de una cuenta nueva (Fase 1 del onboarding).
 *
 * Existe aparte de [HistorialMedicoRepositorioLocal] porque hace dos cosas en
 * una sola transaccion: guarda el expediente y levanta la marca de "requiere
 * onboarding" de la cuenta. Si se hicieran por separado, un cierre de la app
 * entre las dos dejaria un paciente con expediente al que se le vuelve a pedir,
 * o uno sin expediente al que ya no se le pide.
 *
 * El `idPaciente` viene de la sesion, no del DTO: el cuestionario lo arma sin
 * identificador, como lo haria antes de que el backend asignara uno.
 */
class PacienteRepositorioLocal(
    private val base: BaseSalud,
    private val reloj: RelojSalud,
    private val idPaciente: String,
) : PacienteRepositorio {

    override suspend fun registrarPaciente(paciente: PacienteDto): Result<String> =
        withContext(ContextoDeBase) {
            base.transaction {
                val previo = leerExpediente(base, idPaciente)
                guardarExpediente(
                    base,
                    reloj,
                    paciente.copy(
                        idPaciente = idPaciente,
                        // Lo que el cuestionario no captura y ya existia (la
                        // tarjeta RFID, el estado de la cuenta) no se pierde.
                        estadoCuenta = paciente.estadoCuenta ?: previo?.estadoCuenta ?: ESTADO_ACTIVO,
                        dispositivosRfid = paciente.dispositivosRfid.ifEmpty { previo?.dispositivosRfid.orEmpty() },
                    ),
                )
                base.cuentasQueries.marcarOnboardingCompleto(idPaciente)
            }
            Result.success(idPaciente)
        }

    private companion object {
        const val ESTADO_ACTIVO = "activo"
    }
}

internal fun leerExpediente(base: BaseSalud, idPaciente: String): PacienteDto? {
    val fila = base.expedienteQueries.obtener(idPaciente).executeAsOneOrNull() ?: return null
    return runCatching { PacienteJson.decodeFromString(PacienteDto.serializer(), fila.datos_json) }
        .getOrNull()
        ?.copy(idPaciente = idPaciente)
}

/**
 * Alta o edicion del expediente. Los tratamientos se completan antes de
 * guardarse: sin identificador ni horario, el panel no sabria cuando toca cada
 * pastilla ni podria registrar su toma.
 */
internal fun guardarExpediente(
    base: BaseSalud,
    reloj: RelojSalud,
    paciente: PacienteDto,
    riesgo: RiesgoPaciente = RiesgoPaciente.BAJO,
    tomasDesde: String = reloj.fechaHoy(),
) {
    val idPaciente = requireNotNull(paciente.idPaciente)
    val json = PacienteJson.encodeToString(PacienteDto.serializer(), paciente.conTratamientosCompletos())
    val ahora = reloj.instanteActual()
    base.transaction {
        base.expedienteQueries.insertarSiNoExiste(idPaciente, riesgo.name, tomasDesde, json, ahora)
        base.expedienteQueries.actualizarDatos(json, ahora, idPaciente)
    }
}

/** Asigna identificador y horario a los tratamientos que llegan sin ellos. */
internal fun PacienteDto.conTratamientosCompletos(): PacienteDto = copy(
    tratamientosActivos = tratamientosActivos.mapIndexed { indice, tratamiento ->
        val id = tratamiento.idTratamiento
            ?: (HorarioDeTratamiento.idPorDefecto(tratamiento.medicamento) + "_${indice + 1}")
        tratamiento.copy(
            idTratamiento = id,
            horariosSugeridos = HorarioDeTratamiento.horariosDe(tratamiento),
        )
    },
)
