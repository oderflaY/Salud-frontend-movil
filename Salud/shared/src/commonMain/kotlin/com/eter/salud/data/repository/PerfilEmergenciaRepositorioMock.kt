package com.eter.salud.data.repository

import com.eter.salud.domain.model.Alergia
import com.eter.salud.domain.model.IdentidadSupervivencia
import com.eter.salud.domain.model.PerfilEmergenciaReducido
import com.eter.salud.domain.model.PerfilSupervivencia
import com.eter.salud.domain.repository.FalloEmergencia
import com.eter.salud.domain.repository.MotivoFalloEmergencia
import com.eter.salud.domain.repository.PerfilEmergenciaRepositorio

/**
 * Repositorio de emergencia simulado para desarrollo y pruebas de interfaz.
 *
 * Contiene el perfil anclado a la tarjeta fisica `C38610A8`. Cualquier otro
 * identificador devuelve [MotivoFalloEmergencia.TARJETA_DESCONOCIDA], para poder
 * ensayar ese camino acercando una tarjeta de transporte o de hotel.
 *
 * Se sustituira por el cliente HTTP contra el backend en Go sin tocar el
 * ViewModel, que solo depende de [PerfilEmergenciaRepositorio].
 */
class PerfilEmergenciaRepositorioMock(
    perfiles: Map<String, PerfilSupervivencia> = mapOf(TARJETA_DEMO to PERFIL_DEMO),
    private val tarjetasRevocadas: Set<String> = setOf(TARJETA_REVOCADA_DEMO),
) : PerfilEmergenciaRepositorio {

    private val perfilesPorTarjeta = perfiles.mapKeys { it.key.uppercase() }

    override suspend fun consultarPorTarjeta(
        idTarjetaRfid: String,
    ): Result<PerfilSupervivencia> {
        val clave = idTarjetaRfid.uppercase()
        if (clave in tarjetasRevocadas) {
            return Result.failure(FalloEmergencia(MotivoFalloEmergencia.TARJETA_REVOCADA))
        }
        val perfil = perfilesPorTarjeta[clave]
            ?: return Result.failure(
                FalloEmergencia(MotivoFalloEmergencia.TARJETA_DESCONOCIDA),
            )
        return Result.success(perfil)
    }

    companion object {
        /** Tarjeta fisica de pruebas. */
        const val TARJETA_DEMO = "C38610A8"

        /** Tarjeta dada de baja logica, para ensayar el aviso de revocacion. */
        const val TARJETA_REVOCADA_DEMO = "DEADBEEF"

        val PERFIL_DEMO = PerfilSupervivencia(
            idTarjetaRfid = TARJETA_DEMO,
            datosPersonales = IdentidadSupervivencia(
                nombre = "Alfredo",
                apellidos = "Valadez Gonzalez",
                fechaNacimiento = "1998-05-15",
            ),
            perfilEmergenciaReducido = PerfilEmergenciaReducido(
                tipoSangre = "O+",
                donadorOrganos = true,
                alergias = listOf(
                    Alergia(
                        alergeno = "Penicilina",
                        severidad = "Alta (Anafilaxia)",
                        reaccion = "Cierre de vias respiratorias",
                    ),
                ),
                condicionesCriticas = listOf("Asma reactiva"),
                medicacionRescate = listOf("Salbutamol (Inhalador)"),
            ),
        )
    }
}
