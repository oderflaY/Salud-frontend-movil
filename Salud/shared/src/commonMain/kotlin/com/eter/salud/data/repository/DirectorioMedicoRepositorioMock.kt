package com.eter.salud.data.repository

import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.PerfilDoctorDirectorio
import com.eter.salud.domain.repository.DirectorioMedicoRepositorio

/**
 * Directorio Medico simulado para desarrollo y pruebas de interfaz.
 *
 * Reutiliza el `idMedico` de la Dra. Elena Ruiz (`doc_889900A`) del portal de
 * personal medico: en un backend real es la misma cuenta la que aparece aqui y
 * la que inicia sesion como profesional.
 *
 * Se sustituira por el cliente HTTP contra el backend en Go sin tocar el
 * ViewModel, que solo depende de [DirectorioMedicoRepositorio].
 */
class DirectorioMedicoRepositorioMock(
    private val doctores: List<PerfilDoctorDirectorio> = DOCTORES_DEMO,
) : DirectorioMedicoRepositorio {

    private val vinculacionesPorPaciente = mutableMapOf<String, MedicoVinculado>()

    override suspend fun obtenerMedicoVinculado(idPaciente: String): Result<MedicoVinculado?> =
        Result.success(vinculacionesPorPaciente[idPaciente])

    override suspend fun buscarDirectorio(
        especialidad: Especialidad?,
    ): Result<List<PerfilDoctorDirectorio>> {
        val resultado = if (especialidad == null) doctores else {
            doctores.filter { it.especialidad == especialidad }
        }
        return Result.success(resultado)
    }

    override suspend fun solicitarVinculacion(
        idPaciente: String,
        idMedico: String,
    ): Result<MedicoVinculado> {
        val doctor = doctores.firstOrNull { it.idMedico == idMedico }
            ?: return Result.failure(NoSuchElementException("Doctor no encontrado: $idMedico"))
        val vinculado = MedicoVinculado(
            idMedico = doctor.idMedico,
            nombreCompleto = doctor.nombreCompleto,
            especialidad = doctor.especialidad,
            idConversacion = "conv_${idPaciente}_${doctor.idMedico}",
        )
        vinculacionesPorPaciente[idPaciente] = vinculado
        return Result.success(vinculado)
    }

    companion object {
        val DOCTORES_DEMO = listOf(
            // Misma cuenta que la Dra. Elena Ruiz del portal de personal
            // medico (doc_889900A): universidad y disponibilidad tal como se
            // pidieron para probar la tarjeta del directorio.
            PerfilDoctorDirectorio(
                idMedico = "doc_889900A",
                nombreCompleto = "Dra. Elena Ruiz Santos",
                especialidad = Especialidad.CARDIOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Politecnica",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_2",
                nombreCompleto = "Dr. Carlos Mendoza",
                especialidad = Especialidad.MEDICINA_GENERAL,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Durango",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_3",
                nombreCompleto = "Dra. Sofia Torres",
                especialidad = Especialidad.PEDIATRIA,
                cedulaVerificada = true,
                universidad = "Universidad Nacional Autonoma de Mexico",
                disponibilidad = "Disponible manana",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_4",
                nombreCompleto = "Dr. Andres Paredes",
                especialidad = Especialidad.DERMATOLOGIA,
                // Cedula aun en tramite: para ensayar el caso sin la insignia.
                cedulaVerificada = false,
                universidad = "Universidad de Guadalajara",
                disponibilidad = "Disponible en dos dias",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_5",
                nombreCompleto = "Dra. Paula Jimenez",
                especialidad = Especialidad.GINECOLOGIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Nuevo Leon",
                disponibilidad = "Inmediata para chat",
            ),
            PerfilDoctorDirectorio(
                idMedico = "doc_local_6",
                nombreCompleto = "Dr. Ivan Salcedo",
                especialidad = Especialidad.PSIQUIATRIA,
                cedulaVerificada = true,
                universidad = "Universidad Autonoma de Durango",
                disponibilidad = "Disponible manana",
            ),
        )
    }
}
