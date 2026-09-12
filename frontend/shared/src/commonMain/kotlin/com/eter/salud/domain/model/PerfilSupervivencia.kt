package com.eter.salud.domain.model

import kotlinx.serialization.Serializable

/**
 * Respuesta de la API al escanear una tarjeta RFID.
 *
 * Es deliberadamente pobre: DM_HistorialMedico.md, seccion 2 (Segmentacion de
 * Seguridad) limita lo que se entrega sin token de medico tratante al bloque de
 * emergencia. Se le suma la identidad minima porque un paramedico necesita
 * dirigirse al paciente por su nombre y calcular dosis por edad; nada mas.
 *
 * No incluye telefono, CURP, historial ni tratamientos: si este DTO creciera,
 * una tarjeta perdida se convertiria en una fuga de expediente completo.
 */
@Serializable
data class PerfilSupervivencia(
    val idTarjetaRfid: String,
    val datosPersonales: IdentidadSupervivencia,
    val perfilEmergenciaReducido: PerfilEmergenciaReducido,
)

/** Identidad minima que viaja en la respuesta de emergencia. */
@Serializable
data class IdentidadSupervivencia(
    val nombre: String,
    val apellidos: String,
    /** Formato `YYYY-MM-DD`. */
    val fechaNacimiento: String,
) {
    val nombreCompleto: String get() = "$nombre $apellidos"
}
