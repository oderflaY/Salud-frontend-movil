package com.eter.salud.domain.model

/**
 * Una conversacion tal como se ve en la bandeja de entrada.
 *
 * Es un modelo de LECTURA, distinto de [MedicoVinculado] y de [MensajeChat]:
 * junta lo que hace falta para pintar una fila de la bandeja y nada mas. Sin el,
 * la Vista tendria que cruzar la lista de medicos con el historial completo de
 * cada conversacion solo para saber que dice la ultima linea, descargando cientos
 * de mensajes para mostrar uno.
 */
data class ConversacionResumen(
    val idConversacion: String,
    val idMedico: String,
    val nombreMedico: String,
    val especialidad: Especialidad,
    /** Texto del ultimo mensaje, sin recortar: el recorte es decision de la Vista. */
    val ultimoMensaje: String,
    /** Instante ISO 8601 UTC del ultimo mensaje. La Vista lo pasa a hora local. */
    val instanteUltimoMensaje: String,
    val autorUltimoMensaje: AutorMensaje,
    val mensajesSinLeer: Int = 0,
) {
    /** Clave de orden: la conversacion con actividad mas reciente va primero. */
    val claveOrden: String get() = instanteUltimoMensaje
}
