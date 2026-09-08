package com.eter.salud.domain.model

/**
 * Resumen ligero de un paciente vinculado a un profesional
 * (DM_PerfilMedico.md, seccion 2: solo lo que arma el semaforo de riesgo del
 * dashboard, sin descargar el expediente completo de cada paciente).
 */
data class PacienteVinculado(
    val idPaciente: String,
    val nombreCompleto: String,
    val riesgo: RiesgoPaciente,
    /**
     * Canal de chat con este paciente. Es la misma clave que usa el portal del
     * paciente, de modo que ambos lados leen y escriben la misma conversacion.
     */
    val idConversacion: String = "",
)

/** Semaforo de riesgo del paciente, tal como lo entrega el backend. */
enum class RiesgoPaciente {
    ALTO,
    MEDIO,
    BAJO,
}
