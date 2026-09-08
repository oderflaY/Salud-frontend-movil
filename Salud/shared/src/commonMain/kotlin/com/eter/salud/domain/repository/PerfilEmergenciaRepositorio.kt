package com.eter.salud.domain.repository

import com.eter.salud.domain.model.PerfilSupervivencia

/**
 * Consulta de emergencia contra la API en Go.
 *
 * Es el unico contrato que se puede invocar sin sesion de paciente: la tarjeta
 * fisica es la credencial. Por eso devuelve un motivo tipado en lugar de un
 * error generico, para que la pantalla distinga "tarjeta revocada" de "sin red"
 * ante un paciente inconsciente.
 */
interface PerfilEmergenciaRepositorio {

    suspend fun consultarPorTarjeta(idTarjetaRfid: String): Result<PerfilSupervivencia>
}

/** Razones por las que la consulta de emergencia puede no devolver perfil. */
enum class MotivoFalloEmergencia {
    TARJETA_DESCONOCIDA,

    /** Baja logica: la tarjeta existe pero fue reportada como extraviada. */
    TARJETA_REVOCADA,

    SIN_CONEXION,
}

class FalloEmergencia(
    val motivo: MotivoFalloEmergencia,
) : Exception(motivo.name)
