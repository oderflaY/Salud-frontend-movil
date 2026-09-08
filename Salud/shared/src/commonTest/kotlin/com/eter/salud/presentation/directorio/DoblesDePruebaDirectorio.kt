package com.eter.salud.presentation.directorio

import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.PerfilDoctorDirectorio
import com.eter.salud.domain.repository.DirectorioMedicoRepositorio

/** Directorio Medico falso: registra consultas y solicitudes de vinculacion. */
class DirectorioMedicoRepositorioFalso(
    private var medicoVinculado: Result<MedicoVinculado?> = Result.success(null),
    private val doctores: Result<List<PerfilDoctorDirectorio>> = Result.success(DOCTORES_DEMO),
    private val resultadoVinculacion: Result<MedicoVinculado>? = null,
) : DirectorioMedicoRepositorio {

    var consultasVinculado: Int = 0
        private set

    var especialidadesBuscadas: MutableList<Especialidad?> = mutableListOf()
        private set

    var solicitudesVinculacion: MutableList<String> = mutableListOf()
        private set

    override suspend fun obtenerMedicoVinculado(idPaciente: String): Result<MedicoVinculado?> {
        consultasVinculado++
        return medicoVinculado
    }

    override suspend fun buscarDirectorio(
        especialidad: Especialidad?,
    ): Result<List<PerfilDoctorDirectorio>> {
        especialidadesBuscadas += especialidad
        return doctores.map { lista ->
            if (especialidad == null) lista else lista.filter { it.especialidad == especialidad }
        }
    }

    override suspend fun solicitarVinculacion(
        idPaciente: String,
        idMedico: String,
    ): Result<MedicoVinculado> {
        solicitudesVinculacion += idMedico
        val resultado = resultadoVinculacion ?: Result.success(
            DOCTORES_DEMO.first { it.idMedico == idMedico }.let {
                MedicoVinculado(
                    idMedico = it.idMedico,
                    nombreCompleto = it.nombreCompleto,
                    especialidad = it.especialidad,
                    idConversacion = "conv_${idPaciente}_${it.idMedico}",
                )
            },
        )
        if (resultado.isSuccess) {
            medicoVinculado = Result.success(resultado.getOrNull())
        }
        return resultado
    }

    companion object {
        val DOCTORES_DEMO = listOf(
            PerfilDoctorDirectorio(
                idMedico = "doc_889900A",
                nombreCompleto = "Dra. Elena Ruiz Santos",
                especialidad = Especialidad.CARDIOLOGIA,
                cedulaVerificada = true,
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_2",
                nombreCompleto = "Dr. Carlos Mendoza",
                especialidad = Especialidad.MEDICINA_GENERAL,
                cedulaVerificada = true,
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_4",
                nombreCompleto = "Dr. Andres Paredes",
                especialidad = Especialidad.DERMATOLOGIA,
                cedulaVerificada = false,
            ),
        )
    }
}
