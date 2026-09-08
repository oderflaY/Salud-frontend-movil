package com.eter.salud.data.repository

import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.domain.repository.PacientesVinculadosRepositorio

/**
 * Cartera de pacientes simulada para desarrollo y pruebas de interfaz.
 *
 * Contiene los dos pacientes de ejemplo de DM_PerfilMedico.md, seccion 3,
 * ligados a la cuenta de demostracion de la Dra. Elena Ruiz (`doc_889900A`).
 * Se sustituira por el cliente HTTP contra el backend en Go sin tocar el
 * ViewModel, que solo depende de [PacientesVinculadosRepositorio].
 */
class PacientesVinculadosRepositorioMock(
    private val pacientesPorMedico: Map<String, List<PacienteVinculado>> = mapOf(
        ID_MEDICO_DEMO to PACIENTES_DEMO,
    ),
) : PacientesVinculadosRepositorio {

    override suspend fun obtenerPacientesVinculados(
        idMedico: String,
    ): Result<List<PacienteVinculado>> =
        Result.success(pacientesPorMedico[idMedico].orEmpty())

    companion object {
        const val ID_MEDICO_DEMO = "doc_889900A"

        val PACIENTES_DEMO = listOf(
            PacienteVinculado(
                idPaciente = "pac_01H8X9A",
                nombreCompleto = "Juan Perez Gomez",
                riesgo = RiesgoPaciente.ALTO,
                idConversacion = "conv_pac_01H8X9A_$ID_MEDICO_DEMO",
            ),
            PacienteVinculado(
                idPaciente = "pac_02J9Y8B",
                nombreCompleto = "Maria Lopez",
                riesgo = RiesgoPaciente.BAJO,
                idConversacion = "conv_pac_02J9Y8B_$ID_MEDICO_DEMO",
            ),
        )
    }
}
