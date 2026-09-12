package com.eter.salud.data.repository

import com.eter.salud.data.db.BaseSalud
import com.eter.salud.data.local.ContextoDeBase
import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.model.PerfilDoctorDirectorio
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.domain.repository.DirectorioMedicoRepositorio
import com.eter.salud.domain.repository.PacientesVinculadosRepositorio
import com.eter.salud.domain.time.RelojSalud
import kotlinx.coroutines.withContext

/**
 * Directorio Medico con las vinculaciones guardadas en SQLite.
 *
 * El catalogo de doctores es el de [DirectorioMedicoRepositorioMock]: son datos
 * publicos que el backend servira, no algo que el paciente escriba. Lo que SI
 * es del paciente -- con quien se vinculo -- queda en la base y sobrevive a
 * cerrar sesion.
 */
class DirectorioMedicoRepositorioLocal(
    private val base: BaseSalud,
    private val reloj: RelojSalud,
    private val doctores: List<PerfilDoctorDirectorio> = DirectorioMedicoRepositorioMock.DOCTORES_DEMO,
) : DirectorioMedicoRepositorio {

    override suspend fun obtenerMedicoVinculado(idPaciente: String): Result<MedicoVinculado?> =
        obtenerMedicosVinculados(idPaciente).map { it.firstOrNull() }

    override suspend fun obtenerMedicosVinculados(idPaciente: String): Result<List<MedicoVinculado>> =
        withContext(ContextoDeBase) {
            var ids = base.vinculosQueries.medicosDe(idPaciente).executeAsList()
            // Semilla de la bandeja, igual que en la version en memoria: sin ella
            // una cuenta nueva abre "Mi medico" vacio y no tiene con quien hablar.
            if (ids.isEmpty()) {
                base.vinculosQueries.vincular(
                    idPaciente,
                    DirectorioMedicoRepositorioMock.MEDICO_SEMILLA.idMedico,
                    reloj.instanteActual(),
                )
                ids = base.vinculosQueries.medicosDe(idPaciente).executeAsList()
            }
            Result.success(ids.mapNotNull { idMedico -> vinculado(idPaciente, idMedico) })
        }

    override suspend fun buscarDirectorio(especialidad: Especialidad?): Result<List<PerfilDoctorDirectorio>> =
        Result.success(if (especialidad == null) doctores else doctores.filter { it.especialidad == especialidad })

    override suspend fun obtenerPerfilDeMedico(idMedico: String): Result<PerfilDoctorDirectorio?> =
        Result.success(doctores.firstOrNull { it.idMedico == idMedico })

    override suspend fun solicitarVinculacion(
        idPaciente: String,
        idMedico: String,
    ): Result<MedicoVinculado> = withContext(ContextoDeBase) {
        val medico = vinculado(idPaciente, idMedico)
            ?: return@withContext Result.failure(NoSuchElementException("Doctor no encontrado: $idMedico"))
        base.vinculosQueries.vincular(idPaciente, idMedico, reloj.instanteActual())
        Result.success(medico)
    }

    private fun vinculado(idPaciente: String, idMedico: String): MedicoVinculado? =
        doctores.firstOrNull { it.idMedico == idMedico }?.let { doctor ->
            MedicoVinculado(
                idMedico = doctor.idMedico,
                nombreCompleto = doctor.nombreCompleto,
                especialidad = doctor.especialidad,
                idConversacion = idConversacion(idPaciente, doctor.idMedico),
            )
        }
}

/**
 * Cartera del medico: los pacientes vinculados con el, leidos de la misma tabla
 * que la bandeja del paciente. Un paciente que se vincula desde el directorio
 * aparece en la cartera del doctor sin ningun paso extra.
 */
class PacientesVinculadosRepositorioLocal(
    private val base: BaseSalud,
) : PacientesVinculadosRepositorio {

    override suspend fun obtenerPacientesVinculados(idMedico: String): Result<List<PacienteVinculado>> =
        withContext(ContextoDeBase) {
            val pacientes = base.vinculosQueries.pacientesDe(idMedico).executeAsList().mapNotNull { idPaciente ->
                // Sin expediente no hay nada que el medico pueda revisar: una
                // cuenta a medio registrar no entra en su cartera.
                val fila = base.expedienteQueries.obtener(idPaciente).executeAsOneOrNull() ?: return@mapNotNull null
                val datos = leerExpediente(base, idPaciente)?.datosPersonales
                PacienteVinculado(
                    idPaciente = idPaciente,
                    nombreCompleto = datos?.let { "${it.nombre} ${it.apellidos}".trim() }?.ifBlank { null } ?: idPaciente,
                    riesgo = RiesgoPaciente.entries.firstOrNull { it.name == fila.riesgo } ?: RiesgoPaciente.BAJO,
                    idConversacion = idConversacion(idPaciente, idMedico),
                )
            }
            Result.success(pacientes)
        }
}

/** Canal de chat de un paciente con un medico. Mismo formato en ambos portales. */
internal fun idConversacion(idPaciente: String, idMedico: String): String = "conv_${idPaciente}_$idMedico"
