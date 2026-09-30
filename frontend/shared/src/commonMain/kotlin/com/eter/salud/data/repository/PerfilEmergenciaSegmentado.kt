package com.eter.salud.data.repository

import com.eter.salud.data.preferencias.PreferenciasDeTarjeta
import com.eter.salud.domain.model.PerfilSupervivencia
import com.eter.salud.domain.model.segun
import com.eter.salud.domain.repository.PerfilEmergenciaRepositorio
import kotlinx.coroutines.flow.first

/**
 * Aplica, al escanear una tarjeta, lo que su paciente decidio mostrar.
 *
 * Envuelve a cualquier [PerfilEmergenciaRepositorio] (el local o el del
 * backend) y vacia los bloques ocultos antes de que lleguen a la pantalla del
 * escaner.
 *
 * ## Alcance, dicho claro
 *
 * El filtro vive en ESTE telefono. Sirve cuando la tarjeta se escanea en el
 * mismo aparato donde el paciente eligio (la demostracion en modo local). En
 * produccion el escaneo lo hace el telefono de otra persona, que no tiene las
 * preferencias del paciente: ahi el filtro tiene que aplicarlo el backend al
 * armar `perfil-supervivencia`, que es lo que de verdad protege el dato.
 */
class PerfilEmergenciaSegmentado(
    private val base: PerfilEmergenciaRepositorio,
    private val preferencias: PreferenciasDeTarjeta,
) : PerfilEmergenciaRepositorio {

    override suspend fun consultarPorTarjeta(idTarjetaRfid: String): Result<PerfilSupervivencia> =
        base.consultarPorTarjeta(idTarjetaRfid).map { perfil ->
            val visibilidad = preferencias.visibilidad(idTarjetaRfid).first()
            perfil.copy(perfilEmergenciaReducido = perfil.perfilEmergenciaReducido.segun(visibilidad))
        }
}
